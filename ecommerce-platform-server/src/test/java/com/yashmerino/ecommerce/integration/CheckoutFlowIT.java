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
class CheckoutFlowIT {

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
    void fullMarketplaceCheckout() throws Exception {
        var st = conn.createStatement();

        st.execute("""
            INSERT INTO roles(id, name) VALUES (1, 'USER'), (2, 'SELLER'), (3, 'ADMIN'), (4, 'PARTNER')
            """);
        st.execute("""
            INSERT INTO users(id, username, password, photo) VALUES
            (1, 'customer', 'pass', X''),
            (2, 'seller', 'pass', X''),
            (3, 'admin', 'pass', X'')
            """);
        st.execute("INSERT INTO carts(id, user_id) VALUES (1, 1)");
        st.execute("UPDATE users SET cart_id=1 WHERE id=1");
        st.execute("INSERT INTO categories(id, name) VALUES (1, 'Electronics')");
        st.execute("""
            INSERT INTO products(id, user_id, category_id, name, price, on_hand_quantity, reserved_quantity, active) VALUES
            (1, 2, 1, 'Product A', 100.00, 50, 0, TRUE)
            """);
        st.execute("""
            INSERT INTO partners(id, code, name, business_name, email, status, applicant_user_id) VALUES
            (1, 'PTR001', 'Test Partner', 'Test Partner LLC', 'partner@test.com', 'APPROVED', 2)
            """);
        st.execute("""
            INSERT INTO partner_offers(id, partner_id, product_id, partner_sku, price, on_hand_quantity, reserved_quantity, status, approved_at) VALUES
            (1, 1, 1, 'SKU001', 95.00, 100, 0, 'APPROVED', NOW())
            """);

        // Add offer to cart
        st.execute("""
            INSERT INTO cart_items(cart_id, product_id, offer_id, partner_id, name, price, quantity) VALUES
            (1, 1, 1, 1, 'Product A', 95.00, 2)
            """);

        // Verify cart
        var rows = st.executeQuery("SELECT COUNT(*) FROM cart_items WHERE cart_id=1");
        assertTrue(rows.next());
        assertEquals(1, rows.getInt(1));

        // Create order (simulates CheckoutService.checkout)
        st.execute("""
            INSERT INTO orders(id, user_id, total_amount, subtotal, coupon_discount, redeemed_point_value, shipping_fee, currency, status) VALUES
            (1, 1, 190.00, 190.00, 0, 0, 0, 'USD', 'CREATED')
            """);

        st.execute("""
            INSERT INTO order_items(order_id, product_id, offer_id, partner_id, partner_name, name, unit_price, quantity, line_total, qualifying_amount, currency) VALUES
            (1, 1, 1, 1, 'Test Partner', 'Product A', 95.00, 2, 190.00, 190.00, 'USD')
            """);

        // Reserve inventory
        st.execute("""
            UPDATE partner_offers SET reserved_quantity=2, version=version+1 WHERE id=1
            """);

        st.execute("""
            INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
            (1, 'OFFER', 1, 1, 2, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'inv:test:1:1:1')
            """);

        // Create PartnerOrder AWAITING_PAYMENT
        st.execute("""
            INSERT INTO partner_orders(order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_status, created_at, updated_at) VALUES
            (1, 1, 'AWAITING_PAYMENT', 190.00, 0, 0, 19.00, 171.00, 'USD', 'UNSETTLED', NOW(), NOW())
            """);

        // Link order_items to partner_order
        int linked = st.executeUpdate(
            "UPDATE order_items SET partner_order_id=1 WHERE order_id=1 AND partner_id=1");
        assertEquals(1, linked, "order_item linking must update exactly one row");

        // Verify PartnerOrder
        var po = st.executeQuery("SELECT status, settlement_status, partner_payable_amount, currency FROM partner_orders WHERE id=1");
        assertTrue(po.next());
        assertEquals("AWAITING_PAYMENT", po.getString("status"));
        assertEquals("UNSETTLED", po.getString("settlement_status"));
        assertEquals(0, new BigDecimal("171.00").compareTo(po.getBigDecimal("partner_payable_amount")));
        assertEquals("USD", po.getString("currency"));

        // Payment success: AWAITING_PAYMENT → NEW
        st.execute("""
            INSERT INTO payments(id, order_id, amount, currency, status, external_payment_id) VALUES
            (1, 1, 190.00, 'USD', 'SUCCEEDED', 'pi_test_123')
            """);

        int updated = st.executeUpdate(
            "UPDATE partner_orders SET status='NEW', updated_at=NOW() WHERE id=1 AND status='AWAITING_PAYMENT'");
        assertEquals(1, updated);

        // Commit inventory
        st.execute("""
            UPDATE partner_offers SET on_hand_quantity=98, reserved_quantity=0, version=version+1 WHERE id=1
            """);
        st.execute("""
            UPDATE inventory_reservations SET status='COMMITTED' WHERE order_id=1
            """);

        // Full fulfillment flow
        updated = st.executeUpdate(
            "UPDATE partner_orders SET status='ACCEPTED', accepted_at=NOW(), version=version+1, updated_at=NOW() WHERE id=1 AND status='NEW' AND version=0");
        assertEquals(1, updated);
        updated = st.executeUpdate(
            "UPDATE partner_orders SET status='SHIPPED', shipped_at=NOW(), version=version+1, updated_at=NOW() WHERE id=1 AND status='ACCEPTED' AND version=1");
        assertEquals(1, updated);
        updated = st.executeUpdate(
            "UPDATE partner_orders SET status='DELIVERED', delivered_at=NOW(), version=version+1, updated_at=NOW() WHERE id=1 AND status='SHIPPED' AND version=2");
        assertEquals(1, updated);

        // Settlement calculation
        st.execute("""
            INSERT INTO settlements(id, partner_id, period_start, period_end, currency, gross_sales, commission_amount, payable_amount, status) VALUES
            (1, 1, DATE_SUB(NOW(), INTERVAL 1 DAY), DATE_ADD(NOW(), INTERVAL 1 DAY), 'USD', 190.00, 19.00, 171.00, 'CALCULATED')
            """);

        st.execute("""
            INSERT INTO settlement_lines(settlement_id, partner_id, line_type, order_id, partner_order_id, amount, currency, idempotency_key) VALUES
            (1, 1, 'SALE', 1, 1, 190.00, 'USD', 'SALE:1')
            """);

        st.execute("""
            UPDATE partner_orders SET settlement_id=1, settlement_status='SETTLED' WHERE id=1
            """);

        // Verify settlement
        var s = st.executeQuery("SELECT payable_amount, status FROM settlements WHERE id=1");
        assertTrue(s.next());
        assertEquals(0, s.getBigDecimal("payable_amount").compareTo(new BigDecimal("171.00")));
        assertEquals("CALCULATED", s.getString("status"));

        // Verify SALE line exact-once
        var lines = st.executeQuery("SELECT COUNT(*) FROM settlement_lines WHERE idempotency_key='SALE:1'");
        assertTrue(lines.next());
        assertEquals(1, lines.getInt(1));
    }
}
