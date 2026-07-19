package com.yashmerino.ecommerce.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Connection;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class SettlementConcurrencyIT extends MySqlIntegrationTestBase {

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private DataSource dataSource;

    @BeforeEach
    void seedData() {
        jdbc.execute("SET FOREIGN_KEY_CHECKS=0");
        jdbc.execute("TRUNCATE TABLE settlement_lines");
        jdbc.execute("TRUNCATE TABLE settlements");
        jdbc.execute("TRUNCATE TABLE partner_orders");
        jdbc.execute("TRUNCATE TABLE order_items");
        jdbc.execute("TRUNCATE TABLE orders");
        jdbc.execute("TRUNCATE TABLE partner_offers");
        jdbc.execute("TRUNCATE TABLE inventory_reservations");
        jdbc.execute("TRUNCATE TABLE cart_items");
        jdbc.execute("TRUNCATE TABLE products");
        jdbc.execute("TRUNCATE TABLE partners");
        jdbc.execute("TRUNCATE TABLE categories");
        jdbc.execute("TRUNCATE TABLE carts");
        jdbc.execute("TRUNCATE TABLE users");
        jdbc.execute("TRUNCATE TABLE roles");
        jdbc.execute("SET FOREIGN_KEY_CHECKS=1");
        jdbc.update("INSERT INTO roles(id, name) VALUES (1, 'USER'), (2, 'SELLER')");
        jdbc.update("INSERT INTO users(id, username, password, photo) VALUES (1, 'u', 'pass', X''), (2, 's', 'pass', X'')");
        jdbc.update("INSERT INTO carts(id, user_id) VALUES (1, 1)");
        jdbc.update("UPDATE users SET cart_id=1 WHERE id=1");
        jdbc.update("INSERT INTO categories(id, name) VALUES (1, 'C')");
        jdbc.update("INSERT INTO products(id, user_id, category_id, name, price, on_hand_quantity, reserved_quantity, active) " +
                "VALUES (1, 2, 1, 'P', 100.00, 50, 0, TRUE)");
        jdbc.update("INSERT INTO partners(id, code, name, business_name, email, status, applicant_user_id) " +
                "VALUES (1, 'PTR001', 'P', 'P LLC', 'p@t.com', 'APPROVED', 2)");
        jdbc.update("INSERT INTO orders(id, user_id, total_amount, subtotal, currency, status) VALUES (1, 1, 200.00, 200.00, 'USD', 'PAID')");
        jdbc.update("INSERT INTO partner_orders(id, order_id, partner_id, status, subtotal, discount_allocation, shipping_allocation, commission_amount, partner_payable_amount, currency, settlement_status, created_at, updated_at, delivered_at) " +
                "VALUES (1, 1, 1, 'DELIVERED', 200.00, 0, 0, 20.00, 180.00, 'USD', 'UNSETTLED', DATE_SUB(NOW(), INTERVAL 1 HOUR), NOW(), NOW())");
    }

    @Test
    void concurrentSettlementCreatesExactlyOne() throws Exception {
        int threadCount = 3;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    latch.await();
                    try (Connection c = dataSource.getConnection()) {
                        c.setAutoCommit(false);

                        var ps = c.prepareStatement(
                            "INSERT INTO settlements(partner_id, period_start, period_end, currency, status, gross_sales, commission_amount, refund_amount, manual_adjustment, payable_amount, version, created_at, updated_at) " +
                            "VALUES (?, ?, ?, ?, 'OPEN', 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
                            "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)");
                        ps.setLong(1, 1);
                        ps.setTimestamp(2, java.sql.Timestamp.valueOf(LocalDateTime.now().minusDays(1)));
                        ps.setTimestamp(3, java.sql.Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
                        ps.setString(4, "USD");
                        ps.executeUpdate();

                        var idRs = c.createStatement().executeQuery("SELECT LAST_INSERT_ID()");
                        idRs.next();
                        long settlementId = idRs.getLong(1);

                        var lockRs = c.createStatement().executeQuery(
                            "SELECT id, status FROM settlements WHERE id=" + settlementId + " FOR UPDATE");
                        if (!lockRs.next()) {
                            c.rollback();
                            conflictCount.incrementAndGet();
                            return;
                        }
                        String status = lockRs.getString("status");
                        if (!"OPEN".equals(status)) {
                            c.rollback();
                            conflictCount.incrementAndGet();
                            return;
                        }

                        var orderRs = c.createStatement().executeQuery(
                            "SELECT id FROM partner_orders WHERE partner_id=1 AND status='DELIVERED' AND settlement_status='UNSETTLED' AND currency='USD' AND delivered_at >= DATE_SUB(NOW(), INTERVAL 2 DAY) AND delivered_at < DATE_ADD(NOW(), INTERVAL 2 DAY) FOR UPDATE");
                        if (!orderRs.next()) {
                            c.rollback();
                            conflictCount.incrementAndGet();
                            return;
                        }

                        c.createStatement().executeUpdate(
                            "UPDATE settlements SET status='CALCULATED', gross_sales=200.00, commission_amount=20.00, payable_amount=180.00, version=version+1 WHERE id=" + settlementId);
                        c.createStatement().executeUpdate(
                            "UPDATE partner_orders SET settlement_id=" + settlementId + ", settlement_status='SETTLED' WHERE partner_id=1 AND status='DELIVERED' AND settlement_status='UNSETTLED' AND currency='USD'");

                        c.commit();
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    conflictCount.incrementAndGet();
                }
            });
        }

        latch.countDown();
        executor.shutdown();
        Thread.sleep(5000);

        Integer settlementCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlements WHERE partner_id=1 AND currency='USD'", Integer.class);
        assertEquals(1, settlementCount, "Exactly one settlement must exist despite concurrent requests");

        assertEquals(1, successCount.get(), "Exactly one thread should succeed");
        assertTrue(conflictCount.get() > 0, "Other threads should encounter conflicts");

        Integer lineCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM settlement_lines WHERE partner_id=1", Integer.class);
        assertEquals(0, lineCount, "No SALE lines (we didn't insert any in the concurrent flow)");
    }
}
