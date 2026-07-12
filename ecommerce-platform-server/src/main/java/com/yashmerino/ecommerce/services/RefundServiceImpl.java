package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
import com.yashmerino.ecommerce.model.settlement.SettlementStatus;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import com.yashmerino.ecommerce.services.interfaces.RefundService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundServiceImpl implements RefundService {

    private final JdbcTemplate jdbc;
    private final OutboxService outboxService;
    private final PartnerAuthorizationService authz;

    @Override
    @Transactional
    public void processRefund(Long partnerId, Long partnerOrderId, BigDecimal refundAmount, String reason) {
        authz.requireOrderFulfillment(partnerId);

        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);
        if (row.status() != PartnerOrderStatus.RETURN_REQUESTED) {
            throw new InvalidInputException("cannot_refund_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.RETURNED.name(), partnerOrderId,
                PartnerOrderStatus.RETURN_REQUESTED.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        handleSettlementReversal(partnerId, partnerOrderId, refundAmount, row.currency(), row.settlementId(), reason);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_REFUNDED", "partner.order.refunded", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId,
                        "refundAmount", refundAmount, "reason", reason, "status", "RETURNED"),
                "partner-order:refund:" + partnerOrderId);
    }

    private void handleSettlementReversal(Long partnerId, Long partnerOrderId, BigDecimal refundAmount,
                                           String currency, Long orderSettlementId, String reason) {
        SettlementRow target = resolveSettlement(partnerId, currency, orderSettlementId);
        if (target == null) return;

        jdbc.update(
                "INSERT INTO settlement_lines(settlement_id, partner_id, line_type, partner_order_id, amount, currency, description, idempotency_key, created_at) VALUES (?,?,?,?,?,?,?,?,NOW())",
                target.id(), partnerId, "REFUND", partnerOrderId,
                refundAmount.negate(), currency, reason, "refund:" + partnerOrderId);

        jdbc.update(
                "UPDATE settlements SET refund_amount=refund_amount+?, payable_amount=payable_amount-?, version=version+1, updated_at=NOW() WHERE id=? AND version=?",
                refundAmount, refundAmount, target.id(), target.version());
    }

    private SettlementRow resolveSettlement(Long partnerId, String currency, Long orderSettlementId) {
        if (orderSettlementId != null) {
            SettlementRow s = findSettlement(orderSettlementId);
            if (s != null && s.status() != SettlementStatus.APPROVED && s.status() != SettlementStatus.PAID) {
                return s;
            }
        }
        return findOpenSettlement(partnerId, currency);
    }

    private SettlementRow findSettlement(Long settlementId) {
        return jdbc.query(
                "SELECT id, status, version FROM settlements WHERE id=? FOR UPDATE",
                rs -> rs.next()
                        ? new SettlementRow(rs.getLong("id"), SettlementStatus.valueOf(rs.getString("status")), rs.getLong("version"))
                        : null,
                settlementId);
    }

    private SettlementRow findOpenSettlement(Long partnerId, String currency) {
        return jdbc.query(
                "SELECT id, status, version FROM settlements WHERE partner_id=? AND currency=? AND status IN ('OPEN','CALCULATED') ORDER BY created_at DESC LIMIT 1 FOR UPDATE",
                rs -> rs.next()
                        ? new SettlementRow(rs.getLong("id"), SettlementStatus.valueOf(rs.getString("status")), rs.getLong("version"))
                        : null,
                partnerId, currency);
    }

    private PartnerOrderRow selectForUpdate(Long partnerId, Long partnerOrderId) {
        return jdbc.query(
                "SELECT id, order_id, partner_id, status, version, subtotal, discount_allocation, " +
                "shipping_allocation, commission_amount, partner_payable_amount, currency, " +
                "accepted_at, rejected_at, rejection_reason, packed_at, ready_to_ship_at, " +
                "shipped_at, delivered_at, cancelled_at, cancel_reason, created_at, " +
                "settlement_id " +
                "FROM partner_orders WHERE id=? AND partner_id=? FOR UPDATE",
                rs -> {
                    if (!rs.next()) throw new EntityNotFoundException("partner_order_not_found");
                    PartnerOrderStatus status;
                    try {
                        status = PartnerOrderStatus.valueOf(rs.getString("status"));
                    } catch (IllegalArgumentException e) {
                        status = PartnerOrderStatus.NEW;
                    }
                    return new PartnerOrderRow(
                            rs.getLong("id"), rs.getLong("order_id"), rs.getLong("partner_id"),
                            status, rs.getLong("version"),
                            rs.getBigDecimal("subtotal"),
                            rs.getBigDecimal("discount_allocation"),
                            rs.getBigDecimal("shipping_allocation"),
                            rs.getBigDecimal("commission_amount"),
                            rs.getBigDecimal("partner_payable_amount"),
                            rs.getString("currency"),
                            nullableTimestamp(rs, "accepted_at"),
                            nullableTimestamp(rs, "rejected_at"),
                            rs.getString("rejection_reason"),
                            nullableTimestamp(rs, "packed_at"),
                            nullableTimestamp(rs, "ready_to_ship_at"),
                            nullableTimestamp(rs, "shipped_at"),
                            nullableTimestamp(rs, "delivered_at"),
                            nullableTimestamp(rs, "cancelled_at"),
                            rs.getString("cancel_reason"),
                            rs.getTimestamp("created_at").toLocalDateTime(),
                            rs.getObject("settlement_id", Long.class));
                },
                partnerOrderId, partnerId);
    }

    private static LocalDateTime nullableTimestamp(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toLocalDateTime() : null;
    }

    private record PartnerOrderRow(
            Long id, Long orderId, Long partnerId, PartnerOrderStatus status, Long version,
            BigDecimal subtotal, BigDecimal discountAllocation, BigDecimal shippingAllocation,
            BigDecimal commissionAmount, BigDecimal partnerPayableAmount, String currency,
            LocalDateTime acceptedAt, LocalDateTime rejectedAt, String rejectionReason,
            LocalDateTime packedAt, LocalDateTime readyToShipAt,
            LocalDateTime shippedAt, LocalDateTime deliveredAt,
            LocalDateTime cancelledAt, String cancelReason,
            LocalDateTime createdAt, Long settlementId) {}

    private record SettlementRow(Long id, SettlementStatus status, Long version) {}
}
