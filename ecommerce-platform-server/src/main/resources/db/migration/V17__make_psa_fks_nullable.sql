-- V17: Make pending_settlement_adjustments FK columns nullable
-- Residual debt carry-forward entries are not tied to a specific
-- partner_order, refund, or order - just to a partner and currency.

SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_settlement_adjustments' AND COLUMN_NAME = 'partner_order_id' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE pending_settlement_adjustments MODIFY partner_order_id BIGINT NULL',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_settlement_adjustments' AND COLUMN_NAME = 'refund_id' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE pending_settlement_adjustments MODIFY refund_id BIGINT NULL',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_settlement_adjustments' AND COLUMN_NAME = 'order_id' AND IS_NULLABLE = 'NO'),
    'ALTER TABLE pending_settlement_adjustments MODIFY order_id BIGINT NULL',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
