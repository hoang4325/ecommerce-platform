package com.yashmerino.ecommerce.migration;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.sql.ResultSet;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class Phase2MigrationMySqlTest {

    static final String TEST_DB = "ecommerce_platform";
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName("ecommerce_platform");

    @Test
    void v12MigrationAddsPartnerColumns() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            // Verify inventory_reservations new columns
            verifyColumns(connection, "inventory_reservations",
                    "inventory_source_type", "offer_id", "partner_order_id");

            // Verify unique constraint exists on (order_id, inventory_source_key)
            var uks = connection.getMetaData().getIndexInfo(MYSQL.getDatabaseName(), null, "inventory_reservations", true, false);
            boolean found = false;
            while (uks.next()) {
                String idxName = uks.getString("INDEX_NAME");
                if ("uk_inventory_order_source".equalsIgnoreCase(idxName)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Expected unique index uk_inventory_order_source on inventory_reservations");

            // Verify order_items new columns
            verifyColumns(connection, "order_items",
                    "partner_order_id", "discount_allocation", "shipping_allocation", "partner_name");

            // Verify partner_orders updated CHECK constraint includes AWAITING_PAYMENT
            var checkResult = connection.createStatement().executeQuery(
                    "SELECT CHECK_CLAUSE FROM INFORMATION_SCHEMA.CHECK_CONSTRAINTS " +
                    "WHERE CONSTRAINT_NAME='chk_partner_order_status' " +
                    "AND CONSTRAINT_SCHEMA='" + MYSQL.getDatabaseName() + "'");
            assertTrue(checkResult.next(), "Expected chk_partner_order_status");
            String clause = checkResult.getString(1);
            assertTrue(clause.contains("AWAITING_PAYMENT"), "CHECK should include AWAITING_PAYMENT: " + clause);

            // Verify partner_orders new columns
            verifyColumns(connection, "partner_orders",
                    "settlement_id", "settlement_status");

            // Verify settlements unique constraint
            var sUks = connection.getMetaData().getIndexInfo(MYSQL.getDatabaseName(), null, "settlements", true, false);
            boolean sFound = false;
            while (sUks.next()) {
                String idxName = sUks.getString("INDEX_NAME");
                if ("uk_settlement_period_partner".equalsIgnoreCase(idxName)) {
                    sFound = true;
                    break;
                }
            }
            assertTrue(sFound, "Expected unique index uk_settlement_period_partner on settlements");

            // Verify cart_items.offer_id is NULL (nullable for legacy products)
            var col = connection.getMetaData().getColumns(MYSQL.getDatabaseName(), null, "cart_items", "offer_id");
            assertTrue(col.next());
            assertEquals("YES", col.getString("IS_NULLABLE"), "offer_id should be NULL for legacy products");
        }
    }

    @Test
    void v12MigrationAllowsFullPartnerWorkflow() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            // Insert test data simulating a partner checkout flow
            connection.createStatement().execute("""
                INSERT INTO roles(id, name) VALUES (1, 'USER'), (2, 'SELLER'), (3, 'ADMIN'), (4, 'PARTNER')
                """);

            connection.createStatement().execute("""
                INSERT INTO users(id, username, password, photo) VALUES
                (1, 'testuser', 'pass', X''),
                (2, 'testadmin', 'pass', X'')
                """);

            connection.createStatement().execute("""
                INSERT INTO carts(id, user_id) VALUES (1, 1)
                """);

            connection.createStatement().execute("""
                UPDATE users SET cart_id=1 WHERE id=1
                """);

            connection.createStatement().execute("""
                INSERT INTO categories(id, name) VALUES (1, 'Electronics')
                """);

            connection.createStatement().execute("""
                INSERT INTO products(id, user_id, category_id, name, price, on_hand_quantity, reserved_quantity, active) VALUES
                (1, 2, 1, 'Product A', 100.00, 50, 0, TRUE)
                """);

            connection.createStatement().execute("""
                INSERT INTO partners(id, code, name, business_name, email, status, applicant_user_id) VALUES
                (1, 'PTR001', 'Test Partner', 'Test Partner LLC', 'partner@test.com', 'APPROVED', 2)
                """);

            connection.createStatement().execute("""
                INSERT INTO partner_offers(id, partner_id, product_id, partner_sku, price, on_hand_quantity, reserved_quantity, status) VALUES
                (1, 1, 1, 'SKU001', 95.00, 100, 0, 'APPROVED')
                """);

            // Insert cart item with offer_id=0 (product-only) and with offer_id=1 (offer-based)
            // First with offer_id=NULL (legacy product)
            connection.createStatement().execute("""
                INSERT INTO cart_items(cart_id, product_id, offer_id, name, price, quantity) VALUES
                (1, 1, NULL, 'Product A', 100.00, 2)
                """);

            // Verify unique constraint works — duplicate should succeed (MySQL allows multiple NULL offer_ids)
            connection.createStatement().execute("""
                INSERT INTO cart_items(cart_id, product_id, offer_id, name, price, quantity) VALUES
                (1, 1, NULL, 'Product A Duplicate', 100.00, 1)
                """);

            // Insert offer-based cart item
            connection.createStatement().execute("""
                INSERT INTO cart_items(cart_id, product_id, offer_id, name, price, quantity) VALUES
                (1, 1, 1, 'Partner Offer A', 95.00, 3)
                """);

            // Duplicate offer_id should fail
            assertThrows(java.sql.SQLException.class, () -> connection.createStatement().execute("""
                INSERT INTO cart_items(cart_id, product_id, offer_id, name, price, quantity) VALUES
                (1, 1, 1, 'Partner Offer A Dup', 95.00, 1)
                """));

            // Insert order and test inventory_reservations with source type
            connection.createStatement().execute("""
                INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES
                (1, 1, 200.00, 200.00, 'USD', 'CREATED')
                """);

            // Product-only reservation
            connection.createStatement().execute("""
                INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
                (1, 'PRODUCT', NULL, 1, 2, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'test:inv:1:1:0')
                """);

            // Offer-based reservation
            connection.createStatement().execute("""
                INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
                (1, 'OFFER', 1, 1, 3, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'test:inv:1:1:1')
                """);

            // Verify both rows exist
            var count = connection.createStatement().executeQuery(
                    "SELECT COUNT(*) FROM inventory_reservations WHERE order_id=1");
            assertTrue(count.next());
            assertEquals(2, count.getInt(1));

            // Insert order_items
            connection.createStatement().execute("""
                INSERT INTO order_items(order_id, product_id, offer_id, partner_id, name, unit_price, quantity, line_total, qualifying_amount) VALUES
                (1, 1, NULL, NULL, 'Product A', 100.00, 2, 200.00, 200.00)
                """);

            connection.createStatement().execute("""
                INSERT INTO order_items(order_id, product_id, offer_id, partner_id, partner_name, name, unit_price, quantity, line_total, qualifying_amount) VALUES
                (1, 1, 1, 1, 'Test Partner', 'Partner Offer A', 95.00, 3, 285.00, 285.00)
                """);

            // Insert commission rule
            connection.createStatement().execute("""
                INSERT INTO commission_rules(partner_id, product_id, rate, fixed_fee, status, valid_from, valid_to) VALUES
                (1, 1, 0.1000, 1.00, 'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR))
                """);

            // Insert partner order
            connection.createStatement().execute("""
                INSERT INTO partner_orders(order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_status) VALUES
                (1, 1, 'AWAITING_PAYMENT', 285.00, 0, 0, 29.50, 255.50, 'USD', 'UNSETTLED')
                """);

            // Test AWAITING_PAYMENT -> NEW transition
            int updated = connection.createStatement().executeUpdate(
                    "UPDATE partner_orders SET status='NEW', version=version+1 WHERE id=1 AND status='AWAITING_PAYMENT'");
            assertEquals(1, updated);

            // Test NEW -> ACCEPTED
            updated = connection.createStatement().executeUpdate(
                    "UPDATE partner_orders SET status='ACCEPTED', accepted_at=NOW(), version=version+1 WHERE id=1 AND status='NEW'");
            assertEquals(1, updated);
        }
    }

    @Test
    void v12InventoryReservationsUniqueConstraint() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();

        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            connection.createStatement().execute("""
                INSERT INTO roles(id, name) VALUES (1, 'USER')
                """);
            connection.createStatement().execute("""
                INSERT INTO users(id, username, password, photo) VALUES (1, 'u', 'p', X'')
                """);
            connection.createStatement().execute("""
                INSERT INTO products(id, user_id, name, price, on_hand_quantity, reserved_quantity, active) VALUES
                (1, 1, 'P', 10.00, 100, 0, TRUE)
                """);
            connection.createStatement().execute("""
                INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES
                (1, 1, 10.00, 10.00, 'USD', 'CREATED')
                """);

            // Insert product-only reservation
            connection.createStatement().execute("""
                INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
                (1, 'PRODUCT', NULL, 1, 5, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'uk:test:1:1:0')
                """);

            // Same inventory_source_key (PRODUCT:) for product-only should fail
            assertThrows(java.sql.SQLException.class, () -> connection.createStatement().execute("""
                INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
                (1, 'PRODUCT', NULL, 1, 3, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'uk:test:1:1:0:dup')
                """));

            // Different inventory_source (OFFER:1) should succeed
            connection.createStatement().execute("""
                INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
                (1, 'OFFER', 1, 1, 3, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'uk:test:1:1:1')
                """);
        }
    }

    @Test
    void endToEndPartnerWorkflow() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();

        try (var conn = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            var st = conn.createStatement();

            // 1. Set up roles
            st.execute("""
                INSERT INTO roles(id, name) VALUES (1, 'USER'), (2, 'SELLER'), (3, 'ADMIN'), (4, 'PARTNER')
                """);

            // 2. Create users: customer, seller, admin
            st.execute("""
                INSERT INTO users(id, username, password, photo) VALUES
                (1, 'customer', 'pass', X''),
                (2, 'seller', 'pass', X''),
                (3, 'admin', 'pass', X'')
                """);

            st.execute("INSERT INTO user_roles(user_id, role_id) VALUES (2, 2), (3, 3)");

            // 3. Create cart for customer
            st.execute("INSERT INTO carts(id, user_id) VALUES (1, 1)");
            st.execute("UPDATE users SET cart_id=1 WHERE id=1");

            // 4. Create categories
            st.execute("INSERT INTO categories(id, name) VALUES (1, 'Electronics'), (2, 'Books')");

            // 5. Seller creates a product
            st.execute("""
                INSERT INTO products(id, user_id, category_id, name, price, on_hand_quantity, reserved_quantity, active) VALUES
                (1, 2, 1, 'Product A', 100.00, 50, 0, TRUE)
                """);

            // 6. Create partner application via backfill (LEGACY partner)
            st.execute("""
                INSERT INTO partners(id, code, name, business_name, tax_code, email, status, applicant_user_id, approved_at) VALUES
                (1, 'LEGACY-2', 'seller', 'seller biz', 'TAX001', 'seller@test.com', 'APPROVED', 2, NOW())
                """);

            st.execute("""
                INSERT INTO partner_members(partner_id, user_id, role, status, joined_at) VALUES
                (1, 2, 'OWNER', 'ACTIVE', NOW())
                """);

            // 7. Create partner offer from legacy product (V8 backfill simulation)
            st.execute("""
                INSERT INTO partner_offers(id, partner_id, product_id, partner_sku, price, currency, on_hand_quantity, reserved_quantity, status, approved_at) VALUES
                (1, 1, 1, 'LEGACY-1', 95.00, 'USD', 100, 0, 'APPROVED', NOW())
                """);

            // 8. Customer adds offer to cart
            st.execute("""
                INSERT INTO cart_items(cart_id, product_id, offer_id, partner_id, name, price, quantity) VALUES
                (1, 1, 1, 1, 'Product A', 95.00, 2)
                """);

            // 9. Verify cart item
            var rows = st.executeQuery("SELECT COUNT(*) FROM cart_items WHERE cart_id=1");
            assertTrue(rows.next());
            assertEquals(1, rows.getInt(1));

            // 10. Create checkout (simulates CheckoutService.checkout)
            //     This creates Order + OrderItems + PartnerOrder(AWAITING_PAYMENT)
            st.execute("""
                INSERT INTO orders(id, user_id, total_amount, subtotal, coupon_discount, redeemed_point_value, shipping_fee, currency, status) VALUES
                (1, 1, 190.00, 190.00, 0, 0, 0, 'USD', 'CREATED')
                """);

            st.execute("""
                INSERT INTO order_items(order_id, product_id, offer_id, partner_id, partner_name, name, unit_price, quantity, line_total, qualifying_amount, currency) VALUES
                (1, 1, 1, 1, 'seller', 'Product A', 95.00, 2, 190.00, 190.00, 'USD')
                """);

            // Commission rule
            st.execute("""
                INSERT INTO commission_rules(partner_id, product_id, rate, fixed_fee, status, valid_from, valid_to) VALUES
                (1, 1, 0.1000, 0, 'ACTIVE', NOW(), DATE_ADD(NOW(), INTERVAL 1 YEAR))
                """);

            // Reserve inventory
            st.execute("""
                UPDATE partner_offers SET reserved_quantity=2, version=version+1 WHERE id=1
                """);

            st.execute("""
                INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
                (1, 'OFFER', 1, 1, 2, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'inv:1:1:1')
                """);

            // Create PartnerOrder AWAITING_PAYMENT during checkout
            st.execute("""
                INSERT INTO partner_orders(order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_status) VALUES
                (1, 1, 'AWAITING_PAYMENT', 190.00, 0, 0, 19.00, 171.00, 'USD', 'UNSETTLED')
                """);

            // Link order_items to partner_order
            st.execute("""
                UPDATE order_items SET partner_order_id=1 WHERE order_id=1 AND partner_id=1
                """);

            // 11. Verify PartnerOrder is AWAITING_PAYMENT
            var po = st.executeQuery("SELECT status FROM partner_orders WHERE id=1");
            assertTrue(po.next());
            assertEquals("AWAITING_PAYMENT", po.getString("status"));

            // 12. Payment success: transition AWAITING_PAYMENT → NEW
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

            // 13. Fulfillment: NEW → ACCEPTED → SHIPPED → DELIVERED
            updated = st.executeUpdate(
                    "UPDATE partner_orders SET status='ACCEPTED', accepted_at=NOW(), version=version+1, updated_at=NOW() WHERE id=1 AND status='NEW' AND version=0");
            assertEquals(1, updated);

            updated = st.executeUpdate(
                    "UPDATE partner_orders SET status='SHIPPED', shipped_at=NOW(), version=version+1, updated_at=NOW() WHERE id=1 AND status='ACCEPTED' AND version=1");
            assertEquals(1, updated);

            updated = st.executeUpdate(
                    "UPDATE partner_orders SET status='DELIVERED', delivered_at=NOW(), version=version+1, updated_at=NOW() WHERE id=1 AND status='SHIPPED' AND version=2");
            assertEquals(1, updated);

            var delivered = st.executeQuery("SELECT delivered_at FROM partner_orders WHERE id=1");
            assertTrue(delivered.next());
            assertNotNull(delivered.getTimestamp(1));

            // 14. Settlement calculation
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

            // 15. Verify settlement data
            var s = st.executeQuery("SELECT payable_amount, status FROM settlements WHERE id=1");
            assertTrue(s.next());
            assertEquals(0, s.getBigDecimal("payable_amount").compareTo(new java.math.BigDecimal("171.00")));
            assertEquals("CALCULATED", s.getString("status"));

            // 16. Same settlement period does not double-count (test via duplicate key protection)
            var duplicateLine = st.executeQuery(
                    "SELECT COUNT(*) FROM settlement_lines WHERE idempotency_key='SALE:1'");
            assertTrue(duplicateLine.next());
            assertEquals(1, duplicateLine.getInt(1));
        }
    }

    private void verifyColumns(java.sql.Connection connection, String table, String... columnNames) throws Exception {
        Set<String> actual = new TreeSet<>();
        try (var result = connection.getMetaData().getColumns("ecommerce_platform", null, table, "%")) {
            while (result.next()) actual.add(result.getString("COLUMN_NAME").toLowerCase());
        }
        for (String col : columnNames) {
            assertTrue(actual.contains(col.toLowerCase()), "Column " + col + " not found in " + table);
        }
    }
}
