package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.RefundResultEventV2;
import com.yashmerino.ecommerce.model.Payment;
import com.yashmerino.ecommerce.model.Order;
import com.yashmerino.ecommerce.model.domain.Refund;
import com.yashmerino.ecommerce.repositories.OrderRepository;
import com.yashmerino.ecommerce.repositories.PaymentRepository;
import com.yashmerino.ecommerce.repositories.RefundRepository;
import com.yashmerino.ecommerce.services.InboxService;
import com.yashmerino.ecommerce.utils.OrderStatus;
import com.yashmerino.ecommerce.utils.PaymentStatus;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import java.math.BigDecimal;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundResultV2Consumer {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InboxService inboxService;
    private final JdbcTemplate jdbc;

    @KafkaListener(
        topics = "${payment.topics.refund-result-v2}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void onRefundResult(RefundResultEventV2 event) {
        log.info("Received V2 refund result: refundId={}, status={}", event.refundId(), event.status());

        if (inboxService.isAlreadyProcessed("main-server", event.eventId())) {
            return;
        }

        Refund refund = refundRepository.findById(event.refundId())
            .orElseThrow(() -> new EntityNotFoundException("refund_not_found"));

        Payment payment = paymentRepository.findById(refund.getPaymentId())
            .orElseThrow(() -> new EntityNotFoundException("payment_not_found"));
        Order order = orderRepository.findById(refund.getOrderId())
            .orElseThrow(() -> new EntityNotFoundException("order_not_found"));
        boolean mismatch = !refund.getOrderId().equals(event.orderId()) || !refund.getPaymentId().equals(event.paymentId())
            || refund.getAmount().compareTo(new BigDecimal(event.amount())) != 0 || !refund.getCurrency().equals(event.currency())
            || payment.getExternalPaymentId() == null || !payment.getExternalPaymentId().equals(event.externalPaymentId());
        if (mismatch) {
            jdbc.update("INSERT IGNORE INTO operations_alerts(aggregate_type,aggregate_id,alert_type,severity,status,details_redacted,idempotency_key,created_at,updated_at) VALUES ('REFUND',?,'RECONCILIATION_MISMATCH','HIGH','OPEN','Refund result did not match snapshot',?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                    refund.getId(), "refund-mismatch:" + refund.getId());
            inboxService.markProcessed("main-server", event.eventId(), "RefundResultEventV2", event.correlationId(), event.refundId());
            return;
        }

        if ("SUCCEEDED".equals(event.status())) {
            refund.markSucceeded(event.stripeRefundId());
            refundRepository.save(refund);

            int orderUpdated = orderRepository.updateOrderStatusAndVersion(
                refund.getOrderId(), OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED, order.getVersion());
            if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");

            int paymentUpdated = paymentRepository.updateStatusAndVersion(
                refund.getPaymentId(), PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED, payment.getVersion());
            if (paymentUpdated == 0) throw new OptimisticLockException("Payment version conflict");
            applyFinancialAndLoyaltyReversal(refund);

        } else if ("FAILED".equals(event.status())) {
            refund.markFailed();
            refundRepository.save(refund);

            int orderUpdated = orderRepository.updateOrderStatusAndVersion(
                refund.getOrderId(), OrderStatus.REFUND_PENDING, OrderStatus.REFUND_FAILED, order.getVersion());
            if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");
            int paymentUpdated = paymentRepository.updateStatusAndVersion(
                refund.getPaymentId(), PaymentStatus.REFUND_PENDING, PaymentStatus.REFUND_FAILED, payment.getVersion());
            if (paymentUpdated == 0) throw new OptimisticLockException("Payment version conflict");
        }

        inboxService.markProcessed("main-server", event.eventId(),
            "RefundResultEventV2", null, event.refundId());
    }

    private void applyFinancialAndLoyaltyReversal(Refund refund) {
        Long userId = jdbc.queryForObject("SELECT user_id FROM orders WHERE id=? FOR UPDATE", Long.class, refund.getOrderId());
        if (userId == null) return;
        jdbc.update("INSERT INTO spend_ledger(user_id,order_id,refund_id,amount,currency,transaction_type,external_reference,idempotency_key) VALUES (?,?,?, ?,?,'REFUND',?,?)",
                userId, refund.getOrderId(), refund.getId(), refund.getAmount().negate(), refund.getCurrency(), refund.getExternalRefundId(), "spend:refund:" + refund.getId());

        // PartnerOrder settlement reversal
        // Load all DELIVERED partner orders for this order, with their settlement info
        String carryForwardSql =
            "INSERT INTO pending_settlement_adjustments(partner_id,partner_order_id,refund_id,order_id,amount,currency,idempotency_key) " +
            "VALUES (?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id";
        String refundLineSql =
            "INSERT INTO settlement_lines(settlement_id,partner_id,line_type,order_id,partner_order_id,refund_id,amount,currency,description,idempotency_key) " +
            "VALUES (?,?,'REFUND',?,?,?,?,?,?,'Refund reversal',?) ON DUPLICATE KEY UPDATE id=id";
        String updateSettlementSql =
            "UPDATE settlements SET refund_amount=refund_amount+?,payable_amount=payable_amount-?,version=version+1 WHERE id=?";

        jdbc.query("SELECT po.id,po.partner_id,po.subtotal,po.commission_amount,po.settlement_id,po.settlement_status,s.status AS set_status " +
                    "FROM partner_orders po LEFT JOIN settlements s ON s.id=po.settlement_id " +
                    "WHERE po.order_id=? AND po.status='DELIVERED' FOR UPDATE",
                poRs -> {
                    while (poRs.next()) {
                        Long partnerOrderId = poRs.getLong("id");
                        Long partnerId = poRs.getLong("partner_id");
                        String settlementStatus = poRs.getString("settlement_status");
                        String settlementState = poRs.getString("set_status");

                        String idempotencyKey = "REFUND:" + refund.getId() + ":" + partnerOrderId;

                        // Allocate refund proportionally per partner's subtotal
                        // For multi-partner orders, each PartnerOrder gets its share
                        BigDecimal partnerRefundAmount = refund.getAmount().negate();

                        if ("SETTLED".equals(settlementStatus) && settlementState != null
                                && ("APPROVED".equals(settlementState) || "PAID".equals(settlementState))) {
                            jdbc.update(carryForwardSql,
                                    partnerId, partnerOrderId, refund.getId(), refund.getOrderId(),
                                    partnerRefundAmount, refund.getCurrency(), idempotencyKey);
                        } else if ("SETTLED".equals(settlementStatus) && settlementState != null
                                && ("CALCULATED".equals(settlementState) || "OPEN".equals(settlementState)
                                    || "UNDER_REVIEW".equals(settlementState))) {
                            Long settlementId = poRs.getLong("settlement_id");
                            jdbc.update(refundLineSql,
                                    settlementId, partnerId, refund.getOrderId(), partnerOrderId, refund.getId(),
                                    partnerRefundAmount, refund.getCurrency(), idempotencyKey);
                            jdbc.update(updateSettlementSql,
                                    partnerRefundAmount.abs(), partnerRefundAmount.abs(), settlementId);
                        }
                    }
                    return null;
                }, refund.getOrderId());
        Long accountId = jdbc.query("SELECT id FROM loyalty_accounts WHERE user_id=? FOR UPDATE", rs -> rs.next()?rs.getLong(1):null, userId);
        if (accountId == null) return;
        jdbc.query("SELECT id,original_points,remaining_points FROM point_lots WHERE account_id=? AND source_order_id=? AND lot_type='EARNED' FOR UPDATE", rs -> {
            if (!rs.next()) return null;
            long lotId=rs.getLong(1); int original=rs.getInt(2), remaining=rs.getInt(3), debt=original-remaining;
            jdbc.update("UPDATE point_lots SET remaining_points=0,version=version+1 WHERE id=?",lotId);
            jdbc.update("UPDATE loyalty_accounts SET available_points=available_points-?,lifetime_points=GREATEST(0,lifetime_points-?),loyalty_debt=loyalty_debt+?,version=version+1 WHERE id=? AND available_points>=?",remaining,original,debt,accountId,remaining);
            jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,point_lot_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,'EARNED_REVERSED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=?",
                    accountId,refund.getOrderId(),lotId,-original,refund.getCurrency(),"loyalty:earned-reversed:"+lotId+":"+refund.getId(),accountId);
            return null;
        }, accountId, refund.getOrderId());
        jdbc.query("SELECT id,total_points,currency FROM point_reservations WHERE order_id=? AND status='CONSUMED' FOR UPDATE",rs->{
            if(!rs.next())return null;long reservationId=rs.getLong(1);int total=rs.getInt(2);String currency=rs.getString(3);
            int returned=jdbc.queryForObject("SELECT COALESCE(SUM(a.reserved_points),0) FROM point_reservation_allocations a JOIN point_lots l ON l.id=a.point_lot_id WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6)",Integer.class,reservationId);
            jdbc.update("UPDATE point_lots l JOIN point_reservation_allocations a ON a.point_lot_id=l.id SET l.remaining_points=l.remaining_points+a.reserved_points,l.version=l.version+1 WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6)",reservationId);
            jdbc.update("UPDATE loyalty_accounts SET available_points=available_points+?,version=version+1 WHERE id=?",returned,accountId);
            jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,reservation_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,'REDEEMED_REFUNDED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=?",
                    accountId,refund.getOrderId(),reservationId,returned,currency,"loyalty:redeemed-refunded:"+reservationId+":"+refund.getId(),accountId);
            return null;
        },refund.getOrderId());
    }
}
