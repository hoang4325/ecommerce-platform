package com.yashmerino.ecommerce.services;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Component
@RequiredArgsConstructor
public class CheckoutExpiryJob {
    private final JdbcTemplate jdbc;

    @Scheduled(fixedDelayString = "${checkout.expiry-job-delay-ms:30000}")
    @Transactional
    public void expireAwaitingCheckouts() {
        List<Long> orderIds = jdbc.query("SELECT o.id FROM orders o JOIN payments p ON p.order_id=o.id " +
                        "WHERE o.status='CREATED' AND p.status='AWAITING_PAYMENT_METHOD' AND o.reservation_expires_at<=CURRENT_TIMESTAMP(6) " +
                        "ORDER BY o.id LIMIT 100 FOR UPDATE SKIP LOCKED",
                (rs, n) -> rs.getLong(1));
        for (Long orderId : orderIds) expire(orderId);
    }

    private void expire(Long orderId) {
        int paymentChanged = jdbc.update("UPDATE payments p JOIN orders o ON o.id=p.order_id SET p.status='EXPIRED',p.version=p.version+1 " +
                "WHERE p.order_id=? AND p.status='AWAITING_PAYMENT_METHOD' AND o.status='CREATED'", orderId);
        if (paymentChanged != 1) return;
        jdbc.query("SELECT product_id,offer_id,quantity,inventory_source_type FROM inventory_reservations WHERE order_id=? AND status='RESERVED' FOR UPDATE",
                rs -> {
                    while (rs.next()) {
                        String sourceType = rs.getString("inventory_source_type");
                        int qty = rs.getInt("quantity");
                        if ("OFFER".equals(sourceType)) {
                            long offerId = rs.getLong("offer_id");
                            jdbc.update("UPDATE partner_offers SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE id=? AND reserved_quantity>=?",
                                    qty, offerId, qty);
                        } else {
                            long productId = rs.getLong("product_id");
                            jdbc.update("UPDATE products SET reserved_quantity=reserved_quantity-?,version=version+1 WHERE id=? AND reserved_quantity>=?",
                                    qty, productId, qty);
                        }
                    }
                    return null;
                }, orderId);
        jdbc.update("UPDATE inventory_reservations SET status='EXPIRED',version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE order_id=? AND status='RESERVED'", orderId);

        jdbc.query("SELECT id,promotion_id,user_id FROM promotion_reservations WHERE order_id=? AND status='RESERVED' FOR UPDATE", rs -> {
            while (rs.next()) {
                long reservationId = rs.getLong(1), promotionId = rs.getLong(2), userId = rs.getLong(3);
                jdbc.update("UPDATE promotions SET remaining_usage=CASE WHEN remaining_usage IS NULL THEN NULL ELSE remaining_usage+1 END,version=version+1 WHERE id=?", promotionId);
                jdbc.update("UPDATE promotion_usage_counters SET reserved_orders=reserved_orders-1,version=version+1 WHERE promotion_id=? AND user_id=? AND reserved_orders>0", promotionId, userId);
                jdbc.update("UPDATE promotion_reservations SET status='EXPIRED',version=version+1 WHERE id=? AND status='RESERVED'", reservationId);
            }
            return null;
        }, orderId);

        jdbc.query("SELECT id,account_id,total_points,currency FROM point_reservations WHERE order_id=? AND status='PENDING_PAYMENT' FOR UPDATE", rs -> {
            if (!rs.next()) return null;
            long reservationId = rs.getLong(1), accountId = rs.getLong(2); int total = rs.getInt(3); String currency = rs.getString(4);
            int returned = jdbc.queryForObject("SELECT COALESCE(SUM(a.reserved_points),0) FROM point_reservation_allocations a JOIN point_lots l ON l.id=a.point_lot_id WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6)", Integer.class, reservationId);
            jdbc.update("UPDATE point_lots l JOIN point_reservation_allocations a ON a.point_lot_id=l.id SET l.remaining_points=l.remaining_points+a.reserved_points,l.version=l.version+1 WHERE a.reservation_id=? AND l.expires_at>CURRENT_TIMESTAMP(6)", reservationId);
            jdbc.query("SELECT a.point_lot_id,a.reserved_points FROM point_reservation_allocations a JOIN point_lots l ON l.id=a.point_lot_id WHERE a.reservation_id=? AND l.expires_at<=CURRENT_TIMESTAMP(6)", expired -> {
                while (expired.next()) jdbc.update("INSERT IGNORE INTO loyalty_transactions(account_id,order_id,reservation_id,point_lot_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,?,'EXPIRED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=?",
                        accountId, orderId, reservationId, expired.getLong(1), -expired.getInt(2), currency,
                        "loyalty:expired:" + expired.getLong(1) + ":" + reservationId, accountId);
                return null;
            }, reservationId);
            jdbc.update("UPDATE loyalty_accounts SET available_points=available_points+?,reserved_points=reserved_points-?,version=version+1 WHERE id=? AND reserved_points>=?", returned, total, accountId, total);
            String status = returned == 0 ? "EXPIRED" : "RELEASED";
            jdbc.update("UPDATE point_reservations SET status=?,version=version+1,updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND status='PENDING_PAYMENT'", status, reservationId);
            if (returned > 0) jdbc.update("INSERT INTO loyalty_transactions(account_id,order_id,reservation_id,transaction_type,points,value,currency,balance_after,idempotency_key) SELECT ?,?,?,'RELEASED',?,0,?,available_points,? FROM loyalty_accounts WHERE id=?",
                    accountId, orderId, reservationId, returned, currency, "loyalty:released:" + reservationId, accountId);
            return null;
        }, orderId);
        jdbc.update("UPDATE partner_orders SET status='CANCELLED',cancelled_at=NOW(),cancel_reason='checkout_expired',updated_at=NOW() " +
                    "WHERE order_id=? AND status='AWAITING_PAYMENT'", orderId);
        jdbc.update("UPDATE orders SET status='EXPIRED',version=version+1 WHERE id=? AND status='CREATED'", orderId);
    }
}
