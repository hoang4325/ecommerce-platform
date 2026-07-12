-- V15: Repair partner transaction schema
-- Fixes sentinel, missing FKs, inventory source key, pending adjustment lifecycle
-- Roll-forward: safe to apply on any state (V12 applied, partial, or missing)

-- 1. Fix inventory_source_key generated column — never produce OFFER:0
--    Drop old, recreate with clean CASE (no COALESCE)
SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_reservations' AND COLUMN_NAME = 'inventory_source_key'),
    'ALTER TABLE inventory_reservations DROP COLUMN inventory_source_key',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE inventory_reservations
    ADD COLUMN inventory_source_key VARCHAR(100)
    GENERATED ALWAYS AS (
        CASE inventory_source_type
            WHEN 'PRODUCT' THEN CONCAT('PRODUCT:', product_id)
            WHEN 'OFFER' THEN CONCAT('OFFER:', offer_id)
            ELSE NULL
        END
    ) STORED AFTER offer_id;

-- 2. Recreate unique index on the fixed column
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'inventory_reservations' AND INDEX_NAME = 'uk_inventory_order_source'),
    'CREATE UNIQUE INDEX uk_inventory_order_source ON inventory_reservations (order_id, inventory_source_key)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. Add CHECK constraints ensuring inventory_source_type validity
SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_inv_source_type'),
    'ALTER TABLE inventory_reservations DROP CHECK chk_inv_source_type',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

ALTER TABLE inventory_reservations
    ADD CONSTRAINT chk_inv_source_type CHECK (
        (inventory_source_type = 'PRODUCT' AND product_id IS NOT NULL AND offer_id IS NULL)
        OR (inventory_source_type = 'OFFER' AND offer_id IS NOT NULL)
    );

-- 4. Missing FKs on order_items
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND CONSTRAINT_NAME = 'fk_oi_partner_order'),
    'ALTER TABLE order_items ADD CONSTRAINT fk_oi_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND CONSTRAINT_NAME = 'fk_oi_offer'),
    'ALTER TABLE order_items ADD CONSTRAINT fk_oi_offer FOREIGN KEY (offer_id) REFERENCES partner_offers(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND CONSTRAINT_NAME = 'fk_oi_partner'),
    'ALTER TABLE order_items ADD CONSTRAINT fk_oi_partner FOREIGN KEY (partner_id) REFERENCES partners(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'order_items' AND CONSTRAINT_NAME = 'fk_oi_commission_rule'),
    'ALTER TABLE order_items ADD CONSTRAINT fk_oi_commission_rule FOREIGN KEY (commission_rule_id) REFERENCES commission_rules(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 5. FK: settlement_lines.order_item_id → order_items.id
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'settlement_lines' AND CONSTRAINT_NAME = 'fk_sl_order_item'),
    'ALTER TABLE settlement_lines ADD CONSTRAINT fk_sl_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 6. FK: partner_order_commands → partner_orders, partners
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND CONSTRAINT_NAME = 'fk_poc_order'),
    'ALTER TABLE partner_order_commands ADD CONSTRAINT fk_poc_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND CONSTRAINT_NAME = 'fk_poc_partner'),
    'ALTER TABLE partner_order_commands ADD CONSTRAINT fk_poc_partner FOREIGN KEY (partner_id) REFERENCES partners(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 7. Extend pending_settlement_adjustments with lifecycle columns
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_settlement_adjustments' AND COLUMN_NAME = 'claimed_settlement_id'),
    'ALTER TABLE pending_settlement_adjustments
        ADD COLUMN claimed_settlement_id BIGINT DEFAULT NULL AFTER status,
        ADD COLUMN claimed_at DATETIME(6) DEFAULT NULL,
        ADD COLUMN applied_line_id BIGINT DEFAULT NULL,
        ADD COLUMN updated_at DATETIME(6) DEFAULT NULL,
        ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
        ADD INDEX idx_psa_claimed (claimed_settlement_id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_settlement_adjustments' AND CONSTRAINT_NAME = 'fk_psa_claimed_settlement'),
    'ALTER TABLE pending_settlement_adjustments ADD CONSTRAINT fk_psa_claimed_settlement FOREIGN KEY (claimed_settlement_id) REFERENCES settlements(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_settlement_adjustments' AND CONSTRAINT_NAME = 'fk_psa_applied_line'),
    'ALTER TABLE pending_settlement_adjustments ADD CONSTRAINT fk_psa_applied_line FOREIGN KEY (applied_line_id) REFERENCES settlement_lines(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 8. Add period overlap prevention index
--    This helps catch overlapping periods at DB level via unique constraint.
--    Application layer must also check with 409 before insert.
SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements' AND INDEX_NAME = 'uk_settlement_period_partner'),
    'ALTER TABLE settlements DROP INDEX uk_settlement_period_partner',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- Recreate with the same name but also covering status (active periods only)
CREATE UNIQUE INDEX uk_settlement_period_partner ON settlements (partner_id, period_start, period_end, currency);

-- 9. Ensure chk_partner_order_status from V12 is correctly applied
--    (some MySQL < 8.0.19 may have silently failed DROP CONSTRAINT IF EXISTS)
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.CHECK_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND CONSTRAINT_NAME = 'chk_partner_order_status'),
    'ALTER TABLE partner_orders ADD CONSTRAINT chk_partner_order_status CHECK (status IN (''AWAITING_PAYMENT'',''NEW'',''ACCEPTED'',''REJECTED'',''PACKING'',''READY_TO_SHIP'',''SHIPPED'',''DELIVERED'',''CANCELLED'',''RETURN_REQUESTED'',''RETURNED''))',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 10. Add partner_bank_accounts table
CREATE TABLE IF NOT EXISTS partner_bank_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_id BIGINT NOT NULL,
    bank_name VARCHAR(255) NOT NULL,
    account_name VARCHAR(255) NOT NULL,
    masked_account_number VARCHAR(20) NOT NULL,
    encrypted_account_number TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    verified_at DATETIME(6),
    verified_by BIGINT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_pba_partner (partner_id),
    CONSTRAINT fk_pba_partner FOREIGN KEY (partner_id) REFERENCES partners(id),
    CONSTRAINT fk_pba_verified_by FOREIGN KEY (verified_by) REFERENCES users(id)
) ENGINE=InnoDB;
