-- V18: Financial operations ledger, pending adjustment partial lifecycle, audit enrichment
--
-- Phase 1: REVERSED settlement status for PartnerOrders refunded before settlement
-- Phase 2: refund_financial_operations table for business-operation idempotency
-- Phase 5: pending_settlement_adjustments partial lifecycle (original/remaining tracking)
-- Phase 8: partner_order_audit command_type column

-- 1. Add command_type to partner_order_audit for enriched audit trail
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'partner_order_audit' AND COLUMN_NAME = 'command_type'),
    'ALTER TABLE partner_order_audit ADD COLUMN command_type VARCHAR(50) DEFAULT NULL AFTER to_status',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 2. Create refund_financial_operations for business-operation idempotency
CREATE TABLE IF NOT EXISTS refund_financial_operations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_id BIGINT NOT NULL,
    operation_type VARCHAR(50) NOT NULL,
    business_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    error_code VARCHAR(100),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    completed_at DATETIME(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_refund_financial_business_key UNIQUE (business_key),
    CONSTRAINT fk_refund_financial_refund FOREIGN KEY (refund_id) REFERENCES refunds(id),
    INDEX idx_rfo_refund (refund_id),
    INDEX idx_rfo_status (status)
) ENGINE=InnoDB;

-- 3. Extend pending_settlement_adjustments with partial lifecycle columns
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_settlement_adjustments' AND COLUMN_NAME = 'original_amount'),
    'ALTER TABLE pending_settlement_adjustments
        ADD COLUMN original_amount DECIMAL(19,2) DEFAULT NULL AFTER amount,
        ADD COLUMN applied_amount DECIMAL(19,2) DEFAULT NULL,
        ADD COLUMN remaining_amount DECIMAL(19,2) DEFAULT NULL',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
