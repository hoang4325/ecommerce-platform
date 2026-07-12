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

            // Verify unique constraint exists on (order_id, product_id, offer_id)
            var uks = connection.getMetaData().getIndexInfo(MYSQL.getDatabaseName(), null, "inventory_reservations", true, false);
            boolean found = false;
            while (uks.next()) {
                String idxName = uks.getString("INDEX_NAME");
                if ("uk_inventory_order_product".equalsIgnoreCase(idxName)) {
                    found = true;
                    break;
                }
            }
            assertTrue(found, "Expected unique index uk_inventory_order_product on inventory_reservations");

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

            // Verify cart_items unique constraint
            var cUks = connection.getMetaData().getIndexInfo(MYSQL.getDatabaseName(), null, "cart_items", true, false);
            boolean cFound = false;
            while (cUks.next()) {
                String idxName = cUks.getString("INDEX_NAME");
                if ("uk_cart_item_product_offer".equalsIgnoreCase(idxName)) {
                    cFound = true;
                    break;
                }
            }
            assertTrue(cFound, "Expected unique index uk_cart_item_product_offer on cart_items");

            // Verify cart_items.offer_id is NOT NULL
            var col = connection.getMetaData().getColumns(MYSQL.getDatabaseName(), null, "cart_items", "offer_id");
            assertTrue(col.next());
            assertEquals("NO", col.getString("IS_NULLABLE"), "offer_id should be NOT NULL");
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
                INSERT INTO partners(id, code, name, status, applicant_id) VALUES
                (1, 'PTR001', 'Test Partner', 'APPROVED', 2)
                """);

            connection.createStatement().execute("""
                INSERT INTO partner_offers(id, partner_id, product_id, name, price, on_hand_quantity, reserved_quantity, status) VALUES
                (1, 1, 1, 'Partner Offer A', 95.00, 100, 0, 'APPROVED')
                """);

            // Insert cart item with offer_id=0 (product-only) and with offer_id=1 (offer-based)
            // First with offer_id=0 (product)
            connection.createStatement().execute("""
                INSERT INTO cart_items(cart_id, product_id, offer_id, name, price, quantity) VALUES
                (1, 1, 0, 'Product A', 100.00, 2)
                """);

            // Verify unique constraint works — duplicate should fail
            assertThrows(java.sql.SQLException.class, () -> connection.createStatement().execute("""
                INSERT INTO cart_items(cart_id, product_id, offer_id, name, price, quantity) VALUES
                (1, 1, 0, 'Product A Duplicate', 100.00, 1)
                """));

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
                (1, 'PRODUCT', 0, 1, 2, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'test:inv:1:1:0')
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
                (1, 1, 0, NULL, 'Product A', 100.00, 2, 200.00, 200.00)
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
                (1, 'PRODUCT', 0, 1, 5, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'uk:test:1:1:0')
                """);

            // Same (order_id=1, product_id=1, offer_id=0) should fail
            assertThrows(java.sql.SQLException.class, () -> connection.createStatement().execute("""
                INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
                (1, 'PRODUCT', 0, 1, 3, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'uk:test:1:1:0:dup')
                """));

            // Different offer_id (1) should succeed
            connection.createStatement().execute("""
                INSERT INTO inventory_reservations(product_id, inventory_source_type, offer_id, order_id, quantity, status, expires_at, idempotency_key) VALUES
                (1, 'OFFER', 1, 1, 3, 'RESERVED', DATE_ADD(NOW(), INTERVAL 1 HOUR), 'uk:test:1:1:1')
                """);
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
