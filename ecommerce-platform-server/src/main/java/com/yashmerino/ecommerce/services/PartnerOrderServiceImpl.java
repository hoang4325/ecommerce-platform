package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.dto.order.PartnerOrderResponse;
import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
import com.yashmerino.ecommerce.repositories.PartnerOrderRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import com.yashmerino.ecommerce.services.interfaces.PartnerOrderService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.security.MessageDigest;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerOrderServiceImpl implements PartnerOrderService {

    private final PartnerOrderRepository partnerOrderRepository;
    private final PartnerAuthorizationService authz;
    private final JdbcTemplate jdbc;
    private final OutboxService outboxService;

    private static final RowMapper<PartnerOrderRow> ROW_MAPPER = (rs, n) -> {
        PartnerOrderStatus status;
        String rawStatus = rs.getString("status");
        if (rawStatus == null) throw new IllegalStateException("partner_order_status_is_null for id=" + rs.getLong("id"));
        try {
            status = PartnerOrderStatus.valueOf(rawStatus);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("Unknown partner_order_status '" + rawStatus + "' for id=" + rs.getLong("id"), e);
        }
        return new PartnerOrderRow(
                rs.getLong("id"),
                rs.getLong("order_id"),
                rs.getLong("partner_id"),
                status,
                rs.getLong("version"),
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
                rs.getTimestamp("created_at").toLocalDateTime());
    };

    @Override
    @Transactional(readOnly = true)
    public Page<PartnerOrderResponse> getPartnerOrders(Long partnerId, Pageable pageable) {
        authz.requireOrderRead(partnerId);
        return partnerOrderRepository.findByPartnerId(partnerId, pageable).map(PartnerOrderResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerOrderResponse getPartnerOrder(Long partnerId, Long partnerOrderId) {
        authz.requireOrderRead(partnerId);
        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse acceptOrder(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.ACCEPTED) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.NEW) {
            throw new InvalidInputException("cannot_accept_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, accepted_at=NOW(), version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.ACCEPTED.name(), partnerOrderId, PartnerOrderStatus.NEW.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                PartnerOrderStatus.NEW, PartnerOrderStatus.ACCEPTED, null, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_ACCEPTED", "partner.order.accepted", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "ACCEPTED"),
                "partner-order:accept:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse rejectOrder(Long partnerId, Long partnerOrderId, String reason) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.REJECTED) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.NEW) {
            throw new InvalidInputException("cannot_reject_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, rejected_at=NOW(), rejection_reason=?, version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.REJECTED.name(), reason, partnerOrderId, PartnerOrderStatus.NEW.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                PartnerOrderStatus.NEW, PartnerOrderStatus.REJECTED, reason, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_REJECTED", "partner.order.rejected", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "REJECTED", "reason", reason),
                "partner-order:reject:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse markPacking(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.PACKING) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.ACCEPTED) {
            throw new InvalidInputException("cannot_mark_packing_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, packed_at=NOW(), version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.PACKING.name(), partnerOrderId, PartnerOrderStatus.ACCEPTED.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                PartnerOrderStatus.ACCEPTED, PartnerOrderStatus.PACKING, null, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_PACKING", "partner.order.packing", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "PACKING"),
                "partner-order:packing:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse markReadyToShip(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.READY_TO_SHIP) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.PACKING) {
            throw new InvalidInputException("cannot_mark_ready_to_ship_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, ready_to_ship_at=NOW(), version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.READY_TO_SHIP.name(), partnerOrderId, PartnerOrderStatus.PACKING.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                PartnerOrderStatus.PACKING, PartnerOrderStatus.READY_TO_SHIP, null, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_READY_TO_SHIP", "partner.order.ready-to-ship", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "READY_TO_SHIP"),
                "partner-order:ready-to-ship:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse shipOrder(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.SHIPPED) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.READY_TO_SHIP) {
            throw new InvalidInputException("cannot_ship_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, shipped_at=NOW(), version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.SHIPPED.name(), partnerOrderId, PartnerOrderStatus.READY_TO_SHIP.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                PartnerOrderStatus.READY_TO_SHIP, PartnerOrderStatus.SHIPPED, null, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_SHIPPED", "partner.order.shipped", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "SHIPPED"),
                "partner-order:shipped:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse deliverOrder(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.DELIVERED) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.SHIPPED) {
            throw new InvalidInputException("cannot_deliver_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, delivered_at=NOW(), version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.DELIVERED.name(), partnerOrderId, PartnerOrderStatus.SHIPPED.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                PartnerOrderStatus.SHIPPED, PartnerOrderStatus.DELIVERED, null, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_DELIVERED", "partner.order.delivered", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "DELIVERED"),
                "partner-order:delivered:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse cancelOrder(Long partnerId, Long partnerOrderId, String reason) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.CANCELLED) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.NEW && row.status() != PartnerOrderStatus.ACCEPTED) {
            throw new InvalidInputException("cannot_cancel_in_current_status");
        }

        String currentStatus = row.status().name();
        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, cancelled_at=NOW(), cancel_reason=?, version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.CANCELLED.name(), reason, partnerOrderId, currentStatus, row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                row.status(), PartnerOrderStatus.CANCELLED, reason, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_CANCELLED", "partner.order.cancelled", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "CANCELLED", "reason", reason),
                "partner-order:cancel:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse requestReturn(Long partnerId, Long partnerOrderId, String reason) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.RETURN_REQUESTED) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.DELIVERED) {
            throw new InvalidInputException("cannot_request_return_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, cancel_reason=?, version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.RETURN_REQUESTED.name(), reason, partnerOrderId, PartnerOrderStatus.DELIVERED.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                PartnerOrderStatus.DELIVERED, PartnerOrderStatus.RETURN_REQUESTED, reason, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_RETURN_REQUESTED", "partner.order.return-requested", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "RETURN_REQUESTED", "reason", reason),
                "partner-order:return-request:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    @Override
    @Transactional
    public PartnerOrderResponse approveReturn(Long partnerId, Long partnerOrderId) {
        authz.requireOrderFulfillment(partnerId);
        PartnerOrderRow row = selectForUpdate(partnerId, partnerOrderId);

        if (row.status() == PartnerOrderStatus.RETURNED) {
            return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
        }
        if (row.status() != PartnerOrderStatus.RETURN_REQUESTED) {
            throw new InvalidInputException("cannot_approve_return_in_current_status");
        }

        int updated = jdbc.update(
                "UPDATE partner_orders SET status=?, version=version+1, updated_at=NOW() WHERE id=? AND status=? AND version=?",
                PartnerOrderStatus.RETURNED.name(), partnerOrderId, PartnerOrderStatus.RETURN_REQUESTED.name(), row.version());
        if (updated != 1) throw new ConflictException("partner_order_state_changed");

        writeAuditLog(partnerOrderId, partnerId, currentUserId(),
                PartnerOrderStatus.RETURN_REQUESTED, PartnerOrderStatus.RETURNED, null, null, null);

        outboxService.saveOutboxEvent(UUID.randomUUID().toString(), "PARTNER_ORDER", partnerOrderId,
                "PARTNER_ORDER_RETURNED", "partner.order.returned", partnerOrderId.toString(),
                Map.of("partnerOrderId", partnerOrderId, "partnerId", partnerId, "status", "RETURNED"),
                "partner-order:approve-return:" + partnerOrderId);

        return PartnerOrderResponse.from(fetchEntity(partnerId, partnerOrderId));
    }

    private void writeAuditLog(Long partnerOrderId, Long partnerId, Long actorUserId,
                                PartnerOrderStatus fromStatus, PartnerOrderStatus toStatus,
                                String reason, String idempotencyKey, String correlationId) {
        jdbc.update("INSERT INTO partner_order_audit(partner_order_id,partner_id,actor_user_id,from_status,to_status,reason,idempotency_key,correlation_id,occurred_at) " +
                    "VALUES (?,?,?,?,?,?,?,?,NOW())",
                partnerOrderId, partnerId, actorUserId,
                fromStatus != null ? fromStatus.name() : null,
                toStatus != null ? toStatus.name() : null,
                reason, idempotencyKey, correlationId);
    }

    private Long currentUserId() {
        Object principal = org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (!(principal instanceof org.springframework.security.core.userdetails.UserDetails)) return null;
        return jdbc.queryForObject("SELECT id FROM users WHERE username=?",
                Long.class, ((org.springframework.security.core.userdetails.UserDetails) principal).getUsername());
    }

    private PartnerOrderRow selectForUpdate(Long partnerId, Long partnerOrderId) {
        return jdbc.query(
                "SELECT id, order_id, partner_id, status, version, subtotal, discount_allocation, " +
                "shipping_allocation, commission_amount, partner_payable_amount, currency, " +
                "accepted_at, rejected_at, rejection_reason, packed_at, ready_to_ship_at, " +
                "shipped_at, delivered_at, cancelled_at, cancel_reason, created_at " +
                "FROM partner_orders WHERE id=? AND partner_id=? FOR UPDATE",
                ROW_MAPPER, partnerOrderId, partnerId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new EntityNotFoundException("partner_order_not_found"));
    }

    private PartnerOrder fetchEntity(Long partnerId, Long partnerOrderId) {
        return partnerOrderRepository.findByIdAndPartnerId(partnerOrderId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_order_not_found"));
    }

    private static LocalDateTime nullableTimestamp(ResultSet rs, String column) throws SQLException {
        Timestamp ts = rs.getTimestamp(column);
        return ts != null ? ts.toLocalDateTime() : null;
    }

    record PartnerOrderRow(
            Long id,
            Long orderId,
            Long partnerId,
            PartnerOrderStatus status,
            Long version,
            BigDecimal subtotal,
            BigDecimal discountAllocation,
            BigDecimal shippingAllocation,
            BigDecimal commissionAmount,
            BigDecimal partnerPayableAmount,
            String currency,
            LocalDateTime acceptedAt,
            LocalDateTime rejectedAt,
            String rejectionReason,
            LocalDateTime packedAt,
            LocalDateTime readyToShipAt,
            LocalDateTime shippedAt,
            LocalDateTime deliveredAt,
            LocalDateTime cancelledAt,
            String cancelReason,
            LocalDateTime createdAt) {}
}
