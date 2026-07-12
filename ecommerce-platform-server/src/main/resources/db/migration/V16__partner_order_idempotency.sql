-- V16: PartnerOrder command idempotency
-- V14 created partner_order_commands with UNIQUE on idempotency_key alone.
-- This migration alters the table to V16's intended design:
--   - composite unique (idempotency_key, partner_order_id)
--   - executed_at column
--   - idx_poc_status index
--   - command_type VARCHAR(50)
--   - default status PENDING

-- 1. Drop V14's single-column unique on idempotency_key
SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND CONSTRAINT_NAME = 'idempotency_key'),
    'ALTER TABLE partner_order_commands DROP INDEX idempotency_key',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. Drop old V14 unique index on idempotency_key if it has a different name
SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND INDEX_NAME = 'idx_poc_idempotency'),
    'DROP INDEX idx_poc_idempotency ON partner_order_commands',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 3. Add V16 composite unique constraint
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND CONSTRAINT_NAME = 'uk_partner_order_idempotency_key'),
    'ALTER TABLE partner_order_commands ADD CONSTRAINT uk_partner_order_idempotency_key UNIQUE (idempotency_key, partner_order_id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 4. Add executed_at column
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND COLUMN_NAME = 'executed_at'),
    'ALTER TABLE partner_order_commands ADD COLUMN executed_at TIMESTAMP NULL DEFAULT NULL AFTER status',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 5. Add idx_poc_status index
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND INDEX_NAME = 'idx_poc_status'),
    'CREATE INDEX idx_poc_status ON partner_order_commands (status)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 6. Widen command_type to VARCHAR(50) for idempotency use cases
SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND COLUMN_NAME = 'command_type' AND CHARACTER_MAXIMUM_LENGTH = 30),
    'ALTER TABLE partner_order_commands MODIFY command_type VARCHAR(50) NOT NULL',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 7. Add fk_poc_partner FK to partners (idempotent — V15 may already have added it)
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.TABLE_CONSTRAINTS WHERE CONSTRAINT_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_commands' AND CONSTRAINT_NAME = 'fk_poc_partner'),
    'ALTER TABLE partner_order_commands ADD CONSTRAINT fk_poc_partner FOREIGN KEY (partner_id) REFERENCES partners(id)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
