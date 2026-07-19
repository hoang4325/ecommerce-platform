package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.kafka.events.RefundResultEventV2;
import com.yashmerino.ecommerce.model.Payment;
import com.yashmerino.ecommerce.model.Order;
import com.yashmerino.ecommerce.model.domain.Refund;
import com.yashmerino.ecommerce.repositories.OrderRepository;
import com.yashmerino.ecommerce.repositories.PaymentRepository;
import com.yashmerino.ecommerce.repositories.RefundRepository;
import com.yashmerino.ecommerce.services.InboxService;
import com.yashmerino.ecommerce.utils.FinancialOperationHasher;
import com.yashmerino.ecommerce.utils.FinancialOperationHasher.CanonicalPayload;
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
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
@Slf4j
public class RefundResultV2Consumer {

    private static final List<String> ALLOWED_STATUSES = List.of("SUCCEEDED", "FAILED");

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

        if (event.eventId() == null || event.refundId() == null || event.orderId() == null
                || event.paymentId() == null || event.amount() == null || event.currency() == null
                || event.externalPaymentId() == null) {
            log.warn("Refund event missing required fields: eventId={}, refundId={}", event.eventId(), event.refundId());
            return;
        }

        if (!ALLOWED_STATUSES.contains(event.status())) {
            log.warn("Refund event has unknown status={}, creating alert", event.status());
            jdbc.update("INSERT IGNORE INTO operations_alerts(aggregate_type,aggregate_id,alert_type,severity,status,details_redacted,idempotency_key,created_at,updated_at) VALUES ('REFUND',?,'UNKNOWN_EVENT_STATUS','HIGH','OPEN','Refund event with unknown status: '||?,?,CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6))",
                    event.refundId(), event.status(), "unknown-status:" + event.refundId());
            inboxService.markProcessed("main-server", event.eventId(),
                "RefundResultEventV2", null, event.refundId());
            return;
        }

        if (inboxService.isAlreadyProcessed("main-server", event.eventId())) {
            log.info("Refund event {} already processed", event.eventId());
            return;
        }

        Refund refund = refundRepository.findById(event.refundId())
            .orElseThrow(() -> new EntityNotFoundException("refund_not_found"));

        Payment payment = paymentRepository.findById(refund.getPaymentId())
            .orElseThrow(() -> new EntityNotFoundException("payment_not_found"));
        Order order = orderRepository.findById(refund.getOrderId())
            .orElseThrow(() -> new EntityNotFoundException("order_not_found"));

        BigDecimal eventAmount = new BigDecimal(event.amount());
        boolean mismatch = !refund.getOrderId().equals(event.orderId())
            || !refund.getPaymentId().equals(event.paymentId())
            || refund.getAmount().compareTo(eventAmount) != 0
            || !refund.getCurrency().equals(event.currency())
            || payment.getExternalPaymentId() == null
            || !payment.getExternalPaymentId().equals(event.externalPaymentId());
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

        String spendKey = "SPEND_REFUND:" + refund.getId();
        jdbc.update("INSERT IGNORE INTO spend_ledger(user_id,order_id,refund_id,amount,currency,transaction_type,external_reference,idempotency_key) VALUES (?,?,?, ?,?,'REFUND',?,?)",
                userId, refund.getOrderId(), refund.getId(), refund.getAmount().negate(), refund.getCurrency(), refund.getExternalRefundId(), spendKey);

        // -- Fix 1: Per-OrderItem refund allocation --
        // Load order items with partner order info, grouped by partner_order_id
        List<OrderItemRefundRow> allItems = jdbc.query(
            "SELECT oi.id AS order_item_id, oi.partner_order_id, oi.partner_id, oi.unit_price, " +
            "oi.quantity, oi.line_total, oi.coupon_discount_allocation, oi.redeemed_point_allocation, " +
            "oi.commission_amount, oi.partner_payable_amount, oi.currency, " +
            "po.subtotal AS po_subtotal, po.commission_amount AS po_commission, " +
            "po.partner_payable_amount AS po_payable, " +
            "po.settlement_id, po.settlement_status, s.status AS set_status " +
            "FROM order_items oi " +
            "JOIN partner_orders po ON po.id = oi.partner_order_id " +
            "LEFT JOIN settlements s ON s.id = po.settlement_id " +
            "WHERE oi.order_id = ? AND po.status IN ('DELIVERED','RETURN_REQUESTED','RETURNED') " +
            "ORDER BY oi.partner_order_id, oi.id FOR UPDATE",
            (rs, n) -> {
                try {
                    return new OrderItemRefundRow(
                        rs.getLong("order_item_id"),
                        rs.getLong("partner_order_id"),
                        rs.getLong("partner_id"),
                        rs.getBigDecimal("unit_price"),
                        rs.getInt("quantity"),
                        rs.getBigDecimal("line_total"),
                        nullableDecimal(rs, "coupon_discount_allocation"),
                        nullableDecimal(rs, "redeemed_point_allocation"),
                        nullableDecimal(rs, "commission_amount"),
                        nullableDecimal(rs, "partner_payable_amount"),
                        rs.getString("currency"),
                        rs.getBigDecimal("po_subtotal"),
                        nullableDecimal(rs, "po_commission"),
                        nullableDecimal(rs, "po_payable"),
                        rs.getObject("settlement_id", Long.class),
                        rs.getString("settlement_status"),
                        rs.getString("set_status"));
                } catch (Exception e) {
                    throw new RuntimeException("Failed to map order_item row", e);
                }
            },
            refund.getOrderId());

        if (allItems.isEmpty()) return;

        // Calculate total item value to determine refund ratio
        BigDecimal totalItemValue = BigDecimal.ZERO;
        for (OrderItemRefundRow item : allItems) {
            if (item.lineTotal() != null) {
                totalItemValue = totalItemValue.add(item.lineTotal());
            }
        }

        // Refund ratio = refund amount / total order item value
        // Cap at 1.0 (full refund) to prevent over-allocation
        BigDecimal refundAmount = refund.getAmount();
        BigDecimal refundRatio = refundAmount.divide(totalItemValue, 10, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);

        if (refundRatio.compareTo(BigDecimal.ZERO) <= 0) return;

        // Group items by partner_order_id and create RefundItem records
        Map<Long, List<OrderItemRefundRow>> itemsByPartnerOrder = new LinkedHashMap<>();
        for (OrderItemRefundRow item : allItems) {
            itemsByPartnerOrder.computeIfAbsent(item.partnerOrderId(), k -> new ArrayList<>()).add(item);
        }

        // For cumulative refund guard, query already-processed refund totals for this order
        BigDecimal alreadyRefundedGross = jdbc.queryForObject(
            "SELECT COALESCE(SUM(gross_refund_amount),0) FROM refund_items WHERE refund_id IN " +
            "(SELECT r2.id FROM refunds r2 WHERE r2.order_id=? AND r2.status='SUCCEEDED' AND r2.id!=?)",
            BigDecimal.class, refund.getOrderId(), refund.getId() != null ? refund.getId() : 0L);
        if (alreadyRefundedGross == null) alreadyRefundedGross = BigDecimal.ZERO;

        BigDecimal newlyRefundedGross = BigDecimal.ZERO;

        // Create RefundItem records for per-OrderItem allocation
        for (Map.Entry<Long, List<OrderItemRefundRow>> entry : itemsByPartnerOrder.entrySet()) {
            List<OrderItemRefundRow> poItems = entry.getValue();
            if (poItems.isEmpty()) continue;

            OrderItemRefundRow first = poItems.get(0);

            // Calculate partner order total
            BigDecimal poItemValue = BigDecimal.ZERO;
            for (OrderItemRefundRow item : poItems) {
                poItemValue = poItemValue.add(item.lineTotal());
            }

            BigDecimal poRefundRatio = refundAmount.multiply(poItemValue)
                .divide(totalItemValue, 10, RoundingMode.HALF_UP)
                .divide(poItemValue, 10, RoundingMode.HALF_UP)
                .min(BigDecimal.ONE);

            if (poRefundRatio.compareTo(BigDecimal.ZERO) <= 0) continue;

            for (OrderItemRefundRow item : poItems) {
                BigDecimal grossRefund = item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))
                    .multiply(poRefundRatio)
                    .setScale(2, RoundingMode.HALF_UP);

                BigDecimal discountAllocation = item.couponDiscountAllocation() != null
                    ? item.couponDiscountAllocation() : BigDecimal.ZERO;
                BigDecimal pointAllocation = item.redeemedPointAllocation() != null
                    ? item.redeemedPointAllocation() : BigDecimal.ZERO;
                BigDecimal itemDiscount = discountAllocation.add(pointAllocation);
                BigDecimal discountRefund = itemDiscount.multiply(poRefundRatio)
                    .setScale(2, RoundingMode.HALF_UP);

                BigDecimal commissionReversal = item.commissionAmount() != null
                    ? item.commissionAmount().multiply(poRefundRatio).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                BigDecimal payableReversal = item.partnerPayableAmount() != null
                    ? item.partnerPayableAmount().multiply(poRefundRatio).setScale(2, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO;

                // Insert refund_item record
                jdbc.update(
                    "INSERT INTO refund_items(refund_id,order_item_id,partner_order_id,partner_id," +
                    "quantity,gross_refund_amount,discount_refund_amount," +
                    "commission_reversal_amount,partner_payable_reversal_amount,currency) " +
                    "VALUES (?,?,?,?,?,?,?,?,?,?)",
                    refund.getId(), item.orderItemId(), item.partnerOrderId(), item.partnerId(),
                    item.quantity(), grossRefund, discountRefund,
                    commissionReversal, payableReversal, item.currency());

                newlyRefundedGross = newlyRefundedGross.add(grossRefund);
            }
        }

        // Cumulative refund guard
        if (alreadyRefundedGross.add(newlyRefundedGross).compareTo(totalItemValue) > 0) {
            throw new ConflictException("refund_amount_exceeds_remaining_refundable_amount");
        }

        // -- PartnerOrder settlement reversal (now per-PartnerOrder, not full amount) --
        for (Map.Entry<Long, List<OrderItemRefundRow>> entry : itemsByPartnerOrder.entrySet()) {
            List<OrderItemRefundRow> poItems = entry.getValue();
            OrderItemRefundRow first = poItems.get(0);

            BigDecimal poTotalRefund = BigDecimal.ZERO;
            BigDecimal poCommissionReversal = BigDecimal.ZERO;
            BigDecimal poPayableReversal = BigDecimal.ZERO;

            for (OrderItemRefundRow item : poItems) {
                poTotalRefund = poTotalRefund.add(
                    item.unitPrice().multiply(BigDecimal.valueOf(item.quantity()))
                        .multiply(refundRatio).setScale(2, RoundingMode.HALF_UP));
                poCommissionReversal = poCommissionReversal.add(
                    item.commissionAmount() != null
                        ? item.commissionAmount().multiply(refundRatio).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
                poPayableReversal = poPayableReversal.add(
                    item.partnerPayableAmount() != null
                        ? item.partnerPayableAmount().multiply(refundRatio).setScale(2, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO);
            }

            if (poTotalRefund.compareTo(BigDecimal.ZERO) <= 0) continue;

            BigDecimal partnerRefundAmount = poTotalRefund.negate();
            boolean unsettled = !"SETTLED".equals(first.settlementStatus());
            boolean settled = "SETTLED".equals(first.settlementStatus()) && first.settlementState() != null;
            boolean approvedPaid = settled && ("APPROVED".equals(first.settlementState()) || "PAID".equals(first.settlementState()));
            boolean openCalculated = settled && ("OPEN".equals(first.settlementState()) || "CALCULATED".equals(first.settlementState())
                    || "UNDER_REVIEW".equals(first.settlementState()));

            String revKey = "PARTNER_REVERSAL:" + refund.getId() + ":" + first.partnerOrderId();
            String requestHash = FinancialOperationHasher.hash(
                CanonicalPayload.refundOperation(refund.getId(), "PARTNER_FINANCIAL_REVERSAL",
                    refund.getOrderId(), refund.getPaymentId(),
                    first.partnerOrderId(), null, poTotalRefund,
                    first.currency(), null, null));

            if (unsettled) {
                int changed = jdbc.update(
                        "UPDATE partner_orders SET settlement_status='REVERSED',updated_at=CURRENT_TIMESTAMP(6),version=version+1 " +
                        "WHERE id=? AND settlement_status='UNSETTLED'",
                        first.partnerOrderId());
                if (changed == 1) {
                    jdbc.update("INSERT IGNORE INTO refund_financial_operations(refund_id,operation_type,business_key,request_hash,status,completed_at,attempt_count) " +
                            "VALUES (?,'PARTNER_FINANCIAL_REVERSAL',?,?,'COMPLETED',CURRENT_TIMESTAMP(6),1)",
                            refund.getId(), revKey, requestHash);
                }
            } else if (openCalculated) {
                Long settlementId = first.settlementId();
                String lineKey = "REFUND_LINE:" + refund.getId() + ":" + first.partnerOrderId();
                int inserted = jdbc.update(
                        "INSERT INTO settlement_lines(settlement_id,partner_id,line_type,order_id,partner_order_id,refund_id,amount,currency,description,idempotency_key) " +
                        "VALUES (?,?,'REFUND',?,?,?,?,?,'Refund reversal',?) ON DUPLICATE KEY UPDATE id=id",
                        settlementId, first.partnerId(), refund.getOrderId(), first.partnerOrderId(), refund.getId(),
                        partnerRefundAmount, first.currency(), lineKey);
                if (inserted == 1) {
                    int updated = jdbc.update(
                            "UPDATE settlements SET refund_amount=refund_amount+?,payable_amount=payable_amount-?,version=version+1 WHERE id=?",
                            poTotalRefund, poTotalRefund, settlementId);
                    if (updated != 1) throw new ConflictException("settlement_refund_header_conflict");
                }
            } else if (approvedPaid) {
                String ck = "REFUND_CF:" + refund.getId() + ":" + first.partnerOrderId();
                jdbc.update("INSERT INTO pending_settlement_adjustments(partner_id,partner_order_id,refund_id,order_id,amount,original_amount,currency,idempotency_key) " +
                            "VALUES (?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE id=id",
                        first.partnerId(), first.partnerOrderId(), refund.getId(), refund.getOrderId(),
                        partnerRefundAmount, poTotalRefund.negate(), first.currency(), ck);
            }
        }

        // -- Fix 2: Proportional loyalty reversal based on refund_items --
        String loyaltyRevKey = "LOYALTY_EARNED_REFUND:" + refund.getId();
        String loyaltyRevHash = FinancialOperationHasher.hash(
            CanonicalPayload.refundOperation(refund.getId(), "LOYALTY_EARNED_REVERSAL",
                refund.getOrderId(), refund.getPaymentId(),
                null, null, refundAmount, refund.getCurrency(), null, null));

        int opClaimed = jdbc.update(
                "INSERT INTO refund_financial_operations(refund_id,operation_type,business_key,request_hash,status,attempt_count) " +
                "VALUES (?,'LOYALTY_EARNED_REVERSAL',?,?,'PENDING',0) ON DUPLICATE KEY UPDATE id=id",
                refund.getId(), loyaltyRevKey, loyaltyRevHash);

        if (opClaimed == 1) {
            Long accountId = jdbc.query("SELECT id FROM loyalty_accounts WHERE user_id=? FOR UPDATE",
                rs -> rs.next() ? rs.getLong(1) : null, userId);
            if (accountId == null) {
                jdbc.update("UPDATE refund_financial_operations SET status='SKIPPED',error_code='LOYALTY_ACCOUNT_NOT_FOUND'," +
                        "completed_at=CURRENT_TIMESTAMP(6),attempt_count=1 WHERE business_key=?", loyaltyRevKey);
                log.warn("No loyalty account for user {}, skipping earned reversal", userId);
            } else {
                try {
                    doProportionalEarnedReversal(accountId, refund, allItems, refundRatio);
                    jdbc.update("UPDATE refund_financial_operations SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP(6)," +
                            "attempt_count=1 WHERE business_key=?", loyaltyRevKey);
                } catch (Exception e) {
                    jdbc.update("UPDATE refund_financial_operations SET status='FAILED_FINAL',error_code=?,last_error_at=CURRENT_TIMESTAMP(6)," +
                            "attempt_count=attempt_count+1 WHERE business_key=?", e.getMessage(), loyaltyRevKey);
                    log.error("Failed to reverse earned loyalty for refund {}: {}", refund.getId(), e.getMessage());
                }
            }
        }

        String loyaltyRedKey = "LOYALTY_REDEEMED_REFUND:" + refund.getId();
        String loyaltyRedHash = FinancialOperationHasher.hash(
            CanonicalPayload.refundOperation(refund.getId(), "LOYALTY_REDEEMED_RESTORE",
                refund.getOrderId(), refund.getPaymentId(),
                null, null, refundAmount, refund.getCurrency(), null, null));

        int opRedClaimed = jdbc.update(
                "INSERT INTO refund_financial_operations(refund_id,operation_type,business_key,request_hash,status,attempt_count) " +
                "VALUES (?,'LOYALTY_REDEEMED_RESTORE',?,?,'PENDING',0) ON DUPLICATE KEY UPDATE id=id",
                refund.getId(), loyaltyRedKey, loyaltyRedHash);

        if (opRedClaimed == 1) {
            Long accountId = jdbc.query("SELECT id FROM loyalty_accounts WHERE user_id=? FOR UPDATE",
                rs -> rs.next() ? rs.getLong(1) : null, userId);
            if (accountId == null) {
                jdbc.update("UPDATE refund_financial_operations SET status='SKIPPED',error_code='LOYALTY_ACCOUNT_NOT_FOUND'," +
                        "completed_at=CURRENT_TIMESTAMP(6),attempt_count=1 WHERE business_key=?", loyaltyRedKey);
                log.warn("No loyalty account for user {}, skipping redeemed restore", userId);
            } else {
                try {
                    doProportionalRedeemedRestore(accountId, refund, allItems, refundRatio);
                    jdbc.update("UPDATE refund_financial_operations SET status='COMPLETED',completed_at=CURRENT_TIMESTAMP(6)," +
                            "attempt_count=1 WHERE business_key=?", loyaltyRedKey);
                } catch (Exception e) {
                    jdbc.update("UPDATE refund_financial_operations SET status='FAILED_FINAL',error_code=?,last_error_at=CURRENT_TIMESTAMP(6)," +
                            "attempt_count=attempt_count+1 WHERE business_key=?", e.getMessage(), loyaltyRedKey);
                    log.error("Failed to restore redeemed loyalty for refund {}: {}", refund.getId(), e.getMessage());
                }
            }
        }
    }

    private void doProportionalEarnedReversal(Long accountId, Refund refund,
                                               List<OrderItemRefundRow> allItems, BigDecimal refundRatio) {
        String baseKey = "loyalty:refund:" + refund.getId();

        jdbc.query("SELECT id,original_points,remaining_points,source_order_id FROM point_lots WHERE account_id=? AND source_order_id=? AND lot_type='EARNED' FOR UPDATE",
            rs -> {
                long lotId = rs.getLong("id");
                int original = rs.getInt("original_points");
                int remaining = rs.getInt("remaining_points");
                int pointsToReverse = BigDecimal.valueOf(remaining).multiply(refundRatio).setScale(0, RoundingMode.DOWN).intValue();
                if (pointsToReverse <= 0) return null;

                String legacyBase = baseKey + ":earned:" + lotId;
                String idemKey = "LOYALTY_REFUND:" + refund.getId() + ":EARNED:" + lotId;

                // Check if we already reversed some of this lot
                int alreadyReversed = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(points),0) FROM loyalty_refund_allocations WHERE point_lot_id=? AND allocation_type='EARNED_REVERSAL'",
                    Integer.class, lotId);

                int maxReversible = remaining - alreadyReversed;
                if (maxReversible <= 0) return null;

                int actualReversal = Math.min(pointsToReverse, maxReversible);

                jdbc.update("UPDATE point_lots SET remaining_points=remaining_points-?,version=version+1 WHERE id=? AND remaining_points>=?",
                    actualReversal, lotId, actualReversal);

                String txKey = legacyBase + ":" + lotId + ":" + refund.getId();
                jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,point_lot_id,transaction_type,points,value,currency,balance_after,idempotency_key) " +
                        "SELECT ?,?,?,'EARNED_REVERSED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=? ON DUPLICATE KEY UPDATE id=id",
                        accountId, refund.getOrderId(), lotId, -actualReversal, refund.getCurrency(), txKey, accountId);

                jdbc.update("INSERT INTO loyalty_refund_allocations(refund_id,point_lot_id,allocation_type,points,idempotency_key) VALUES (?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE points=points",
                        refund.getId(), lotId, "EARNED_REVERSAL", actualReversal, idemKey);

                jdbc.update("UPDATE loyalty_accounts SET available_points=GREATEST(0,available_points-?),lifetime_points=GREATEST(0,lifetime_points-?),version=version+1 WHERE id=?",
                    actualReversal, actualReversal, accountId);
                return null;
            },
            accountId, refund.getOrderId());
    }

    private void doProportionalRedeemedRestore(Long accountId, Refund refund,
                                                List<OrderItemRefundRow> allItems, BigDecimal refundRatio) {
        String baseKey = "loyalty:refund:" + refund.getId();

        jdbc.query("SELECT id,total_points,currency FROM point_reservations WHERE order_id=? AND status='CONSUMED' FOR UPDATE",
            rs -> {
                long reservationId = rs.getLong("id");
                int total = rs.getInt("total_points");
                String currency = rs.getString("currency");

                // Already restored
                int alreadyRestored = jdbc.queryForObject(
                    "SELECT COALESCE(SUM(points),0) FROM loyalty_refund_allocations WHERE reservation_id=? AND allocation_type='REDEEMED_RESTORE'",
                    Integer.class, reservationId);

                int maxRestorable = total - alreadyRestored;
                if (maxRestorable <= 0) return null;

                int pointsToRestore = BigDecimal.valueOf(maxRestorable).multiply(refundRatio).setScale(0, RoundingMode.DOWN).intValue();
                if (pointsToRestore <= 0) return null;

                int actualToRestore = Math.min(pointsToRestore, maxRestorable);
                int[] remaining = {actualToRestore};

                jdbc.query("SELECT a.id AS alloc_id, a.reserved_points, l.id AS lot_id, l.remaining_points, l.expires_at " +
                    "FROM point_reservation_allocations a JOIN point_lots l ON l.id = a.point_lot_id " +
                    "WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6) FOR UPDATE",
                    allocRs -> {
                        while (allocRs.next() && remaining[0] > 0) {
                            long lotId = allocRs.getLong("lot_id");
                            int allocPoints = allocRs.getInt("reserved_points");
                            int restoreFromLot = Math.min(remaining[0], allocPoints);
                            jdbc.update("UPDATE point_lots SET remaining_points=remaining_points+?,version=version+1 WHERE id=?",
                                restoreFromLot, lotId);
                            remaining[0] -= restoreFromLot;
                        }
                        return null;
                    },
                    reservationId);

                int restored = actualToRestore - remaining[0];

                String txKey = baseKey + ":" + reservationId + ":" + refund.getId();
                jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,reservation_id,transaction_type,points,value,currency,balance_after,idempotency_key) " +
                        "SELECT ?,?,?,'REDEEMED_REFUNDED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=? ON DUPLICATE KEY UPDATE id=id",
                        accountId, refund.getOrderId(), reservationId, restored, currency, txKey, accountId);

                jdbc.update("UPDATE loyalty_accounts SET available_points=available_points+?,version=version+1 WHERE id=?", restored, accountId);

                String idemKey = "LOYALTY_REFUND:" + refund.getId() + ":REDEEMED:" + reservationId;
                jdbc.update("INSERT INTO loyalty_refund_allocations(refund_id,reservation_id,allocation_type,points,idempotency_key) VALUES (?,?,?,?,?) " +
                        "ON DUPLICATE KEY UPDATE points=points",
                        refund.getId(), reservationId, "REDEEMED_RESTORE", restored, idemKey);
                return null;
            },
            refund.getOrderId());
    }

    private static BigDecimal nullableDecimal(java.sql.ResultSet rs, String column) {
        try {
            java.math.BigDecimal val = rs.getBigDecimal(column);
            return val;
        } catch (java.sql.SQLException e) {
            return null;
        }
    }

    private record OrderItemRefundRow(
            Long orderItemId, Long partnerOrderId, Long partnerId,
            BigDecimal unitPrice, int quantity, BigDecimal lineTotal,
            BigDecimal couponDiscountAllocation, BigDecimal redeemedPointAllocation,
            BigDecimal commissionAmount, BigDecimal partnerPayableAmount,
            String currency,
            BigDecimal poSubtotal, BigDecimal poCommissionAmount, BigDecimal poPayable,
            Long settlementId, String settlementStatus, String settlementState) {}

    private record PartnerOrderRefundRow(
            Long id, Long partnerId,
            BigDecimal subtotal, BigDecimal commissionAmount, BigDecimal partnerPayableAmount,
            Long settlementId, String settlementStatus, String settlementState) {}
}
