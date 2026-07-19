-- V19: Partial refund per RefundItem, loyalty refund allocation, operation lifecycle
--
-- Fix 1: refund_items for per-OrderItem refund allocation (partial refund support)
-- Fix 2: loyalty_refund_allocations for proportional loyalty reversal
-- Fix 3: refund_financial_operations lifecycle columns (attempt_count, lease, error)
-- Fix 4: pending_settlement_adjustments backfill for original/applied/remaining
-- Fix 5: Allow PARTIALLY_APPLIED status in adjustment query

-- 1. Create refund_items for per-OrderItem refund tracking
CREATE TABLE IF NOT EXISTS refund_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_id BIGINT NOT NULL,
    order_item_id BIGINT NOT NULL,
    partner_order_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    quantity INT NOT NULL,
    gross_refund_amount DECIMAL(19,2) NOT NULL,
    discount_refund_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    commission_reversal_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    partner_payable_reversal_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    currency VARCHAR(3) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_ri_refund FOREIGN KEY (refund_id) REFERENCES refunds(id),
    CONSTRAINT fk_ri_order_item FOREIGN KEY (order_item_id) REFERENCES order_items(id),
    CONSTRAINT fk_ri_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders(id),
    INDEX idx_ri_refund (refund_id),
    INDEX idx_ri_order_item (order_item_id),
    INDEX idx_ri_partner_order (partner_order_id)
) ENGINE=InnoDB;

-- 2. Create loyalty_refund_allocations for proportional loyalty reversal
CREATE TABLE IF NOT EXISTS loyalty_refund_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_id BIGINT NOT NULL,
    refund_item_id BIGINT,
    point_lot_id BIGINT,
    reservation_id BIGINT,
    allocation_type VARCHAR(20) NOT NULL,
    points INT NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT uk_lra_idempotency UNIQUE (idempotency_key),
    CONSTRAINT fk_lra_refund FOREIGN KEY (refund_id) REFERENCES refunds(id),
    INDEX idx_lra_refund (refund_id),
    INDEX idx_lra_lot (point_lot_id),
    INDEX idx_lra_reservation (reservation_id)
) ENGINE=InnoDB;

-- 3. Extend refund_financial_operations with lifecycle columns
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'refund_financial_operations' AND COLUMN_NAME = 'attempt_count'),
    'ALTER TABLE refund_financial_operations
        ADD COLUMN attempt_count INT NOT NULL DEFAULT 0,
        ADD COLUMN last_error_code VARCHAR(100) DEFAULT NULL,
        ADD COLUMN last_error_at DATETIME(6) DEFAULT NULL,
        ADD COLUMN lease_until DATETIME(6) DEFAULT NULL,
        ADD COLUMN processing_started_at DATETIME(6) DEFAULT NULL',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 4. Backfill original_amount, applied_amount, remaining_amount for existing pending adjustments
SET @stmt = (SELECT IF(EXISTS(SELECT 1 FROM information_schema.COLUMNS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'pending_settlement_adjustments' AND COLUMN_NAME = 'original_amount'),
    'UPDATE pending_settlement_adjustments SET
        original_amount = COALESCE(original_amount, amount),
        applied_amount = COALESCE(applied_amount, 0),
        remaining_amount = COALESCE(remaining_amount, ABS(amount) - COALESCE(applied_amount, 0))
     WHERE original_amount IS NULL',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;

-- 5. Ensure settlement unique constraint exists for partner/period/currency
SET @stmt = (SELECT IF(NOT EXISTS(SELECT 1 FROM information_schema.STATISTICS WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'settlements' AND INDEX_NAME = 'uk_settlement_partner_period_currency'),
    'ALTER TABLE settlements ADD CONSTRAINT uk_settlement_partner_period_currency UNIQUE (partner_id, period_start, period_end, currency)',
    'SELECT 1'));
PREPARE s FROM @stmt; EXECUTE s; DEALLOCATE PREPARE s;
