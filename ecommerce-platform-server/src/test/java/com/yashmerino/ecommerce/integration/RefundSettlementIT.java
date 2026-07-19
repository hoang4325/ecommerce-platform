package com.yashmerino.ecommerce.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

@Transactional
class RefundSettlementIT extends MySqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    void seedBaseData() {
        jdbc.execute("INSERT INTO roles(id, name) VALUES (1, 'USER'), (2, 'SELLER'), (3, 'ADMIN')");
        jdbc.execute("INSERT INTO users(id, username, password, photo) VALUES (1, 'u', 'pass', X''), (2, 's', 'pass', X'')");
        jdbc.execute("INSERT INTO carts(id, user_id) VALUES (1, 1)");
        jdbc.execute("UPDATE users SET cart_id=1 WHERE id=1");
        jdbc.execute("INSERT INTO categories(id, name) VALUES (1, 'Cat')");
        jdbc.execute("INSERT INTO products(id, user_id, category_id, name, price, on_hand_quantity, reserved_quantity, active) " +
                "VALUES (1, 2, 1, 'P', 100.00, 50, 0, TRUE)");
        jdbc.execute("INSERT INTO partners(id, code, name, business_name, email, status, applicant_user_id) " +
                "VALUES (1, 'PTR001', 'P', 'P LLC', 'p@t.com', 'APPROVED', 2)");
    }

    @Test
    void refundBeforeSettlementCreatesReversedNoFalseDebt() {
        jdbc.update("INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES (1, 1, 100.00, 100.00, 'USD', 'PAID')");
        jdbc.update("INSERT INTO payments(id, order_id, amount, currency, status, external_payment_id) VALUES (1, 1, 100.00, 'USD', 'SUCCEEDED', 'pi_test')");
        jdbc.update("INSERT INTO refunds(id, order_id, payment_id, amount, currency, reason, requested_by, status, request_idempotency_key) " +
                "VALUES (1, 1, 1, 100.00, 'USD', 'test', 1, 'SUCCEEDED', 'refund-ik-1')");
        jdbc.update("INSERT INTO order_items(order_id, product_id, offer_id, partner_id, partner_name, name, unit_price, quantity, line_total, qualifying_amount, currency) " +
                "VALUES (1, 1, NULL, 1, 'P', 'P', 100.00, 1, 100.00, 100.00, 'USD')");
        jdbc.update("INSERT INTO partner_orders(id, order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_status, created_at, updated_at) " +
                "VALUES (1, 1, 1, 'RETURNED', 1000.00, 0, 0, 100.00, 900.00, 'USD', 'UNSETTLED', NOW(), NOW())");

        int changed = jdbc.update(
            "UPDATE partner_orders SET settlement_status='REVERSED', updated_at=CURRENT_TIMESTAMP(6), version=version+1 " +
            "WHERE id=1 AND settlement_status='UNSETTLED'");
        assertEquals(1, changed, "UNSETTLED PartnerOrder must flip to REVERSED");

        Integer adjustments = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pending_settlement_adjustments WHERE partner_id=1", Integer.class);
        assertEquals(0, adjustments, "No pending debt should exist for reversed order");

        String settlementStatus = jdbc.queryForObject(
            "SELECT settlement_status FROM partner_orders WHERE id=1", String.class);
        assertEquals("REVERSED", settlementStatus);
    }

    @Test
    void refundAfterPaidSettlementCreatesCarryForward() {
        jdbc.update("INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES (1, 1, 100.00, 100.00, 'USD', 'PAID')");
        jdbc.update("INSERT INTO payments(id, order_id, amount, currency, status, external_payment_id) VALUES (1, 1, 100.00, 'USD', 'SUCCEEDED', 'pi_test')");
        jdbc.update("INSERT INTO refunds(id, order_id, payment_id, amount, currency, reason, requested_by, status, request_idempotency_key) " +
                "VALUES (1, 1, 1, 100.00, 'USD', 'test', 1, 'SUCCEEDED', 'refund-ik-2')");
        jdbc.update("INSERT INTO order_items(order_id, product_id, offer_id, partner_id, partner_name, name, unit_price, quantity, line_total, qualifying_amount, currency) " +
                "VALUES (1, 1, NULL, 1, 'P', 'P', 100.00, 1, 100.00, 100.00, 'USD')");
        jdbc.update("INSERT INTO settlements(id, partner_id, period_start, period_end, currency, gross_sales, commission_amount, payable_amount, status) " +
                "VALUES (1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 'USD', 100.00, 10.00, 90.00, 'PAID')");
        jdbc.update("INSERT INTO partner_orders(id, order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_id, settlement_status, created_at, updated_at) " +
                "VALUES (1, 1, 1, 'DELIVERED', 100.00, 0, 0, 10.00, 90.00, 'USD', 1, 'SETTLED', NOW(), NOW())");
        jdbc.update("INSERT INTO settlement_lines(settlement_id, partner_id, line_type, order_id, partner_order_id, amount, currency, idempotency_key) " +
                "VALUES (1, 1, 'SALE', 1, 1, 100.00, 'USD', 'SALE:1')");

        jdbc.update("INSERT INTO pending_settlement_adjustments(partner_id, partner_order_id, refund_id, order_id, amount, original_amount, currency, idempotency_key) " +
                "VALUES (1, 1, 1, 1, -90.00, -90.00, 'USD', 'REFUND_CF:1:1')");

        Integer adjustments = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pending_settlement_adjustments WHERE partner_id=1 AND status='PENDING'", Integer.class);
        assertEquals(1, adjustments, "Exactly one pending adjustment for carry-forward");

        jdbc.update("INSERT INTO pending_settlement_adjustments(partner_id, partner_order_id, refund_id, order_id, amount, original_amount, currency, idempotency_key) " +
                "VALUES (1, 1, 1, 1, -90.00, -90.00, 'USD', 'REFUND_CF:1:1') " +
                "ON DUPLICATE KEY UPDATE id=id");

        adjustments = jdbc.queryForObject(
            "SELECT COUNT(*) FROM pending_settlement_adjustments WHERE partner_id=1 AND status='PENDING'", Integer.class);
        assertEquals(1, adjustments, "Retry must not create duplicate pending adjustment");

        BigDecimal payable = jdbc.queryForObject(
            "SELECT payable_amount FROM settlements WHERE id=1", BigDecimal.class);
        assertEquals(0, payable.compareTo(new BigDecimal("90.00")));
        String status = jdbc.queryForObject(
            "SELECT status FROM settlements WHERE id=1", String.class);
        assertEquals("PAID", status);
    }

    @Test
    void currencyFiltering() {
        jdbc.update("INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES (1, 1, 100.00, 100.00, 'USD', 'PAID')");
        jdbc.update("INSERT INTO partner_orders(id, order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_status, created_at, updated_at) " +
                "VALUES (1, 1, 1, 'DELIVERED', 100.00, 0, 0, 10.00, 90.00, 'USD', 'UNSETTLED', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()), " +
                "(2, 1, 1, 'DELIVERED', 100.00, 0, 0, 10.00, 90.00, 'EUR', 'UNSETTLED', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW())");

        Integer usdOrders = jdbc.queryForObject(
            "SELECT COUNT(*) FROM partner_orders po WHERE po.partner_id=1 AND po.status='DELIVERED' AND po.settlement_status='UNSETTLED' AND po.currency='USD' AND po.delivered_at >= DATE_SUB(NOW(), INTERVAL 2 DAY) AND po.delivered_at < DATE_ADD(NOW(), INTERVAL 2 DAY)",
            Integer.class);
        assertEquals(1, usdOrders, "USD settlement should find 1 USD order");

        Integer eurOrders = jdbc.queryForObject(
            "SELECT COUNT(*) FROM partner_orders po WHERE po.partner_id=1 AND po.status='DELIVERED' AND po.settlement_status='UNSETTLED' AND po.currency='EUR' AND po.delivered_at >= DATE_SUB(NOW(), INTERVAL 2 DAY) AND po.delivered_at < DATE_ADD(NOW(), INTERVAL 2 DAY)",
            Integer.class);
        assertEquals(1, eurOrders, "EUR settlement should find 1 EUR order");
    }
}
