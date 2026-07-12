-- V14: Add pending_settlement_adjustments, missing FKs, indexes, and idempotency
-- Addresses P0 gaps:
--   - Pending settlement carry-forward (RefundResultV2Consumer)
--   - Settlement atomic claim via lock
--   - Missing FKs on inventory_reservations
--   - Missing indexes for partner_order flow
--   - partner_order_commands idempotency table
--   - order_items.currency NOT NULL migration

-- 1. Create pending_settlement_adjustments for refunds that need carry-forward
--    (when refund occurs after settlement is APPROVED/PAID)
CREATE TABLE IF NOT EXISTS pending_settlement_adjustments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    partner_order_id BIGINT NOT NULL,
    refund_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    reason VARCHAR(500) NOT NULL DEFAULT 'carry-forward',
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_psa_partner (partner_id),
    INDEX idx_psa_status (status),
    INDEX idx_psa_idempotency (idempotency_key),
    CONSTRAINT fk_psa_partner FOREIGN KEY (partner_id) REFERENCES partners(id),
    CONSTRAINT fk_psa_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders(id),
    CONSTRAINT fk_psa_refund FOREIGN KEY (refund_id) REFERENCES refunds(id)
) ENGINE=InnoDB;

-- 2. Create partner_order_commands for request idempotency
CREATE TABLE IF NOT EXISTS partner_order_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_order_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    command_type VARCHAR(30) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL UNIQUE,
    request_hash VARCHAR(64) NOT NULL,
    response_snapshot TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'COMPLETED',
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_poc_order (partner_order_id),
    INDEX idx_poc_idempotency (idempotency_key)
) ENGINE=InnoDB;

-- 3. Add FK: inventory_reservations.offer_id → partner_offers.id
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_reservations' AND CONSTRAINT_NAME = 'fk_inv_reservation_offer'),
    'ALTER TABLE inventory_reservations ADD CONSTRAINT fk_inv_reservation_offer FOREIGN KEY (offer_id) REFERENCES partner_offers(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 4. Add FK: inventory_reservations.partner_order_id → partner_orders.id
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_reservations' AND CONSTRAINT_NAME = 'fk_inv_reservation_partner_order'),
    'ALTER TABLE inventory_reservations ADD CONSTRAINT fk_inv_reservation_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 5. Add index: inventory_reservations(order_id, status)
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_reservations' AND INDEX_NAME = 'idx_inv_order_status'),
    'CREATE INDEX idx_inv_order_status ON inventory_reservations (order_id, status)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 6. Add index: inventory_reservations(offer_id, status)
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_reservations' AND INDEX_NAME = 'idx_inv_offer_status'),
    'CREATE INDEX idx_inv_offer_status ON inventory_reservations (offer_id, status)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 7. Add index: partner_orders(settlement_status, delivered_at) for settlement queries
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_orders' AND INDEX_NAME = 'idx_po_settle_delivered'),
    'CREATE INDEX idx_po_settle_delivered ON partner_orders (settlement_status, delivered_at)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 8. Drop old partner_orders CHECK constraint and recreate (V12 used IF EXISTS, some MySQL versions may have left it)
SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_partner_order_status'),
    'ALTER TABLE partner_orders DROP CHECK chk_partner_order_status',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_partner_order_status'),
    'ALTER TABLE partner_orders ADD CONSTRAINT chk_partner_order_status CHECK (status IN (''AWAITING_PAYMENT'',''NEW'',''ACCEPTED'',''REJECTED'',''PACKING'',''READY_TO_SHIP'',''SHIPPED'',''DELIVERED'',''CANCELLED'',''RETURN_REQUESTED'',''RETURNED''))',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
