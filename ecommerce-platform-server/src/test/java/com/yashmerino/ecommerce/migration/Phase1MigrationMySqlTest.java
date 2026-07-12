package com.yashmerino.ecommerce.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.DriverManager;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
class Phase1MigrationMySqlTest {
    @Container
    static final MySQLContainer<?> MYSQL = new MySQLContainer<>("mysql:8.0.39")
            .withDatabaseName("ecommerce_platform");

    @Test
    void migratesFromCleanDatabaseWithMySqlSemantics() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
        Set<String> tables = new TreeSet<>();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var result = connection.getMetaData().getTables(MYSQL.getDatabaseName(), null, "%", new String[]{"TABLE"})) {
            while (result.next()) tables.add(result.getString("TABLE_NAME").toLowerCase());
        }
        assertTrue(tables.containsAll(Set.of("checkout_requests", "payment_initiation_requests", "order_items",
                "inventory_reservations", "promotion_reservations", "promotion_usage_counters",
                "point_reservations", "point_reservation_allocations", "point_lots",
                "loyalty_accounts", "loyalty_transactions", "promotions", "outbox_events")));
    }

    @Test
    void verifyV4ColumnTypes() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            verifyColumns(connection, "orders", "subtotal", "coupon_discount", "redeemed_point_value",
                    "currency", "reservation_expires_at", "payment_deadline", "version");
            verifyColumns(connection, "payments", "currency", "version");
            verifyColumns(connection, "products", "on_hand_quantity", "reserved_quantity", "active", "version");
            verifyColumns(connection, "checkout_requests", "idempotency_key", "request_hash", "response_snapshot", "status");
            verifyColumns(connection, "outbox_events", "event_id", "aggregate_type", "event_type", "topic", "payload", "status");
        }
    }

    @Test
    void upgradeFromV3ToV4() throws Exception {
        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .target(MigrationVersion.fromVersion("3"))
                .load()
                .migrate();
        Set<String> preTables = new TreeSet<>();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var result = connection.getMetaData().getTables(MYSQL.getDatabaseName(), null, "%", new String[]{"TABLE"})) {
            while (result.next()) preTables.add(result.getString("TABLE_NAME").toLowerCase());
        }
        assertTrue(preTables.containsAll(Set.of("orders", "payments", "products", "cart_items")));
        assertTrue(preTables.stream().noneMatch(t -> t.contains("checkout") || t.contains("outbox") || t.contains("inventory_") || t.contains("promotion_") || t.contains("point_") || t.contains("loyalty_")));

        Flyway.configure()
                .dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())
                .load()
                .migrate();
        Set<String> postTables = new TreeSet<>();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword());
             var result = connection.getMetaData().getTables(MYSQL.getDatabaseName(), null, "%", new String[]{"TABLE"})) {
            while (result.next()) postTables.add(result.getString("TABLE_NAME").toLowerCase());
        }
        assertTrue(postTables.containsAll(Set.of("checkout_requests", "outbox_events", "inventory_reservations",
                "promotion_reservations", "promotion_usage_counters", "point_reservations",
                "point_reservation_allocations", "loyalty_accounts", "loyalty_transactions", "order_items")));
    }

    @Test
    void verifyDoubleToDecimalConversion() throws Exception {
        Flyway.configure().dataSource(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword()).load().migrate();
        try (var connection = DriverManager.getConnection(MYSQL.getJdbcUrl(), MYSQL.getUsername(), MYSQL.getPassword())) {
            var col = connection.getMetaData().getColumns(MYSQL.getDatabaseName(), null, "products", "price");
            assertTrue(col.next());
            assertTrue(col.getString("TYPE_NAME").toUpperCase().contains("DECIMAL"));
        }
    }

    private void verifyColumns(java.sql.Connection connection, String table, String... columnNames) throws Exception {
        Set<String> actual = new TreeSet<>();
        try (var result = connection.getMetaData().getColumns(MYSQL.getDatabaseName(), null, table, "%")) {
            while (result.next()) actual.add(result.getString("COLUMN_NAME").toLowerCase());
        }
        for (String col : columnNames) {
            assertTrue(actual.contains(col.toLowerCase()), "Column " + col + " not found in " + table);
        }
    }
}
