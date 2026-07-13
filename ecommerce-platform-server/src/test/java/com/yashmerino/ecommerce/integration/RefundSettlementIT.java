package com.yashmerino.ecommerce.integration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.DriverManager;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers
class RefundSettlementIT {

    static final String TEST_DB = "ecommerce_platform";

    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName(TEST_DB);

    private static Connection conn;

    @BeforeAll
    static void setup() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();
        conn = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
    }

    @Test
    void refundBeforeSettlementCreatesReversedNoFalseDebt() throws Exception {
        var st = conn.createStatement();

        // Setup base data
        st.execute("INSERT INTO roles(id, name) VALUES (1, 'USER'), (2, 'SELLER'), (3, 'ADMIN')");
        st.execute("INSERT INTO users(id, username, password, photo) VALUES (1, 'u', 'pass', X''), (2, 's', 'pass', X'')");
        st.execute("INSERT INTO carts(id, user_id) VALUES (1, 1)");
        st.execute("UPDATE users SET cart_id=1 WHERE id=1");
        st.execute("INSERT INTO categories(id, name) VALUES (1, 'Cat')");
        st.execute("""
            INSERT INTO products(id, user_id, category_id, name, price, on_hand_quantity, reserved_quantity, active) VALUES
            (1, 2, 1, 'P', 100.00, 50, 0, TRUE)
            """);
        st.execute("""
            INSERT INTO partners(id, code, name, business_name, email, status, applicant_user_id) VALUES
            (1, 'PTR001', 'P', 'P LLC', 'p@t.com', 'APPROVED', 2)
            """);

        // Create order + payment + refund
        st.execute("""
            INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES
            (1, 1, 100.00, 100.00, 'USD', 'PAID')
            """);
        st.execute("""
            INSERT INTO payments(id, order_id, amount, currency, status, external_payment_id) VALUES
            (1, 1, 100.00, 'USD', 'SUCCEEDED', 'pi_test')
            """);
        st.execute("""
            INSERT INTO refunds(id, order_id, payment_id, amount, currency, reason, requested_by, status, request_idempotency_key) VALUES
            (1, 1, 1, 100.00, 'USD', 'test', 1, 'SUCCEEDED', 'refund-ik-1')
            """);

        // Create PartnerOrder: RETURNED, UNSETTLED, payable = 900
        st.execute("""
            INSERT INTO order_items(order_id, product_id, offer_id, partner_id, partner_name, name, unit_price, quantity, line_total, qualifying_amount, currency) VALUES
            (1, 1, NULL, 1, 'P', 'P', 100.00, 1, 100.00, 100.00, 'USD')
            """);

        st.execute("""
            INSERT INTO partner_orders(id, order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_status, created_at, updated_at) VALUES
            (1, 1, 1, 'RETURNED', 1000.00, 0, 0, 100.00, 900.00, 'USD', 'UNSETTLED', NOW(), NOW())
            """);

        // Simulate the refund consumer's financial reversal logic:
        // For UNSETTLED PartnerOrder, it should REVERSE, not create pending debt
        int changed = st.executeUpdate(
            "UPDATE partner_orders SET settlement_status='REVERSED', updated_at=CURRENT_TIMESTAMP(6), version=version+1 " +
            "WHERE id=1 AND settlement_status='UNSETTLED'");
        assertEquals(1, changed, "UNSETTLED PartnerOrder must flip to REVERSED");

        // Verify no pending adjustment created for the reversed order
        var adjustments = st.executeQuery(
            "SELECT COUNT(*) FROM pending_settlement_adjustments WHERE partner_id=1");
        assertTrue(adjustments.next());
        assertEquals(0, adjustments.getInt(1), "No pending debt should exist for reversed order");

        // Verify PartnerOrder is now REVERSED
        var po = st.executeQuery("SELECT settlement_status FROM partner_orders WHERE id=1");
        assertTrue(po.next());
        assertEquals("REVERSED", po.getString("settlement_status"));
    }

    @Test
    void refundAfterPaidSettlementCreatesCarryForward() throws Exception {
        var st = conn.createStatement();

        // Setup
        st.execute("INSERT INTO roles(id, name) VALUES (1, 'USER'), (2, 'SELLER'), (3, 'ADMIN')");
        st.execute("INSERT INTO users(id, username, password, photo) VALUES (1, 'u', 'pass', X''), (2, 's', 'pass', X'')");
        st.execute("INSERT INTO carts(id, user_id) VALUES (1, 1)");
        st.execute("UPDATE users SET cart_id=1 WHERE id=1");
        st.execute("INSERT INTO categories(id, name) VALUES (1, 'Cat')");
        st.execute("""
            INSERT INTO products(id, user_id, category_id, name, price, on_hand_quantity, reserved_quantity, active) VALUES
            (1, 2, 1, 'P', 100.00, 50, 0, TRUE)
            """);
        st.execute("""
            INSERT INTO partners(id, code, name, business_name, email, status, applicant_user_id) VALUES
            (1, 'PTR001', 'P', 'P LLC', 'p@t.com', 'APPROVED', 2)
            """);
        st.execute("""
            INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES
            (1, 1, 100.00, 100.00, 'USD', 'PAID')
            """);
        st.execute("""
            INSERT INTO payments(id, order_id, amount, currency, status, external_payment_id) VALUES
            (1, 1, 100.00, 'USD', 'SUCCEEDED', 'pi_test')
            """);
        st.execute("""
            INSERT INTO refunds(id, order_id, payment_id, amount, currency, reason, requested_by, status, request_idempotency_key) VALUES
            (1, 1, 1, 100.00, 'USD', 'test', 1, 'SUCCEEDED', 'refund-ik-2')
            """);
        st.execute("""
            INSERT INTO order_items(order_id, product_id, offer_id, partner_id, partner_name, name, unit_price, quantity, line_total, qualifying_amount, currency) VALUES
            (1, 1, NULL, 1, 'P', 'P', 100.00, 1, 100.00, 100.00, 'USD')
            """);

        // PartnerOrder: DELIVERED, SETTLED, settlement PAID
        st.execute("""
            INSERT INTO settlements(id, partner_id, period_start, period_end, currency, gross_sales, commission_amount, payable_amount, status) VALUES
            (1, 1, DATE_SUB(NOW(), INTERVAL 2 DAY), DATE_SUB(NOW(), INTERVAL 1 DAY), 'USD', 100.00, 10.00, 90.00, 'PAID')
            """);

        st.execute("""
            INSERT INTO partner_orders(id, order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_id, settlement_status, created_at, updated_at) VALUES
            (1, 1, 1, 'DELIVERED', 100.00, 0, 0, 10.00, 90.00, 'USD', 1, 'SETTLED', NOW(), NOW())
            """);

        st.execute("""
            INSERT INTO settlement_lines(settlement_id, partner_id, line_type, order_id, partner_order_id, amount, currency, idempotency_key) VALUES
            (1, 1, 'SALE', 1, 1, 100.00, 'USD', 'SALE:1')
            """);

        // Simulate refund carry-forward for APPROVED/PAID settlement
        st.execute("""
            INSERT INTO pending_settlement_adjustments(partner_id, partner_order_id, refund_id, order_id, amount, original_amount, currency, idempotency_key) VALUES
            (1, 1, 1, 1, -90.00, -90.00, 'USD', 'REFUND_CF:1:1')
            """);

        // Verify one pending adjustment exists
        var adjustments = st.executeQuery(
            "SELECT COUNT(*) FROM pending_settlement_adjustments WHERE partner_id=1 AND status='PENDING'");
        assertTrue(adjustments.next());
        assertEquals(1, adjustments.getInt(1), "Exactly one pending adjustment for carry-forward");

        // Retry idempotency: same key should not duplicate
        st.execute("""
            INSERT INTO pending_settlement_adjustments(partner_id, partner_order_id, refund_id, order_id, amount, original_amount, currency, idempotency_key)
            VALUES (1, 1, 1, 1, -90.00, -90.00, 'USD', 'REFUND_CF:1:1')
            ON DUPLICATE KEY UPDATE id=id
            """);

        adjustments = st.executeQuery(
            "SELECT COUNT(*) FROM pending_settlement_adjustments WHERE partner_id=1 AND status='PENDING'");
        assertTrue(adjustments.next());
        assertEquals(1, adjustments.getInt(1), "Retry must not create duplicate pending adjustment");

        // Old settlement must remain unchanged
        var settlement = st.executeQuery("SELECT payable_amount, status FROM settlements WHERE id=1");
        assertTrue(settlement.next());
        assertEquals(0, settlement.getBigDecimal("payable_amount").compareTo(new BigDecimal("90.00")));
        assertEquals("PAID", settlement.getString("status"));
    }

    @Test
    void currencyFiltering() throws Exception {
        var st = conn.createStatement();

        st.execute("INSERT INTO roles(id, name) VALUES (1, 'USER')");
        st.execute("INSERT INTO users(id, username, password, photo) VALUES (1, 'u', 'pass', X'')");
        st.execute("INSERT INTO carts(id, user_id) VALUES (1, 1)");
        st.execute("UPDATE users SET cart_id=1 WHERE id=1");
        st.execute("INSERT INTO categories(id, name) VALUES (1, 'Cat')");
        st.execute("""
            INSERT INTO products(id, user_id, category_id, name, price, on_hand_quantity, reserved_quantity, active) VALUES
            (1, 2, 1, 'P', 100.00, 50, 0, TRUE)
            """);
        st.execute("""
            INSERT INTO partners(id, code, name, business_name, email, status, applicant_user_id) VALUES
            (1, 'PTR001', 'P', 'P LLC', 'p@t.com', 'APPROVED', 2)
            """);
        st.execute("""
            INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES
            (1, 1, 100.00, 100.00, 'USD', 'PAID')
            """);

        // Create partner orders in different currencies
        st.execute("""
            INSERT INTO partner_orders(id, order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_status, created_at, updated_at) VALUES
            (1, 1, 1, 'DELIVERED', 100.00, 0, 0, 10.00, 90.00, 'USD', 'UNSETTLED', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW()),
            (2, 1, 1, 'DELIVERED', 100.00, 0, 0, 10.00, 90.00, 'EUR', 'UNSETTLED', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW())
            """);

        // USD settlement should only pick up USD order
        var usdOrders = st.executeQuery(
            "SELECT COUNT(*) FROM partner_orders po WHERE po.partner_id=1 AND po.status='DELIVERED' AND po.settlement_status='UNSETTLED' AND po.currency='USD' AND po.delivered_at >= DATE_SUB(NOW(), INTERVAL 2 DAY) AND po.delivered_at < DATE_ADD(NOW(), INTERVAL 2 DAY)");
        assertTrue(usdOrders.next());
        assertEquals(1, usdOrders.getInt(1), "USD settlement should find 1 USD order");

        var eurOrders = st.executeQuery(
            "SELECT COUNT(*) FROM partner_orders po WHERE po.partner_id=1 AND po.status='DELIVERED' AND po.settlement_status='UNSETTLED' AND po.currency='EUR' AND po.delivered_at >= DATE_SUB(NOW(), INTERVAL 2 DAY) AND po.delivered_at < DATE_ADD(NOW(), INTERVAL 2 DAY)");
        assertTrue(eurOrders.next());
        assertEquals(1, eurOrders.getInt(1), "EUR settlement should find 1 EUR order");
    }
}
