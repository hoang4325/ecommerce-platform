-- V13: Fix V12 sentinel approach and add missing constraints
-- V12 used offer_id = 0 sentinel which conflicts with FK constraints.
-- This migration:
--   - Fixes cart_items.offer_id to use NULL (not 0) for legacy products
--   - Adds inventory_source_key generated column for proper unique constraint
--   - Adds CHECK constraints for stock integrity
--   - Adds missing columns (currency, order_item_id)
--   - Creates partner_order_audit table

-- 1. Fix inventory_reservations.offer_id to allow NULL
ALTER TABLE inventory_reservations MODIFY offer_id BIGINT NULL;

-- 2. Drop V12's bad unique index on cart_items that used offer_id=0 sentinel
DROP INDEX IF EXISTS uk_cart_item_product_offer ON cart_items;

-- 3. Revert cart_items.offer_id from 0 sentinel back to NULL for legacy items
UPDATE cart_items SET offer_id = NULL WHERE offer_id = 0;

-- 4. Add inventory_source_key generated column for proper unique constraint
ALTER TABLE inventory_reservations
    ADD COLUMN inventory_source_key VARCHAR(100)
    GENERATED ALWAYS AS (
        CONCAT(inventory_source_type, ':',
               CASE WHEN inventory_source_type = 'OFFER' THEN COALESCE(CAST(offer_id AS CHAR), '0')
                    ELSE CAST(product_id AS CHAR) END)
    ) STORED
    AFTER offer_id;

-- 5. Drop old uk_inventory_order_product (order_id, product_id) which prevented
--    multiple reservations per order for different offer/products
ALTER TABLE inventory_reservations DROP INDEX uk_inventory_order_product;

-- 6. Add proper unique constraint using inventory_source_key
CREATE UNIQUE INDEX uk_inventory_order_source ON inventory_reservations (order_id, inventory_source_key);

-- 7. Add CHECK constraint for inventory_source_type
ALTER TABLE inventory_reservations
    ADD CONSTRAINT chk_inv_source_type CHECK (inventory_source_type IN ('PRODUCT', 'OFFER'));

-- 8. Add stock integrity constraints on products
ALTER TABLE products
    ADD CONSTRAINT chk_product_stock CHECK (on_hand_quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= on_hand_quantity);

-- 9. Add stock integrity constraints on partner_offers
ALTER TABLE partner_offers
    ADD CONSTRAINT chk_offer_stock CHECK (on_hand_quantity >= 0 AND reserved_quantity >= 0 AND reserved_quantity <= on_hand_quantity);

-- 10. Add currency column to order_items for partner settlement
ALTER TABLE order_items ADD COLUMN currency VARCHAR(3) DEFAULT NULL AFTER partner_payable_amount;

-- 11. Add order_item_id to settlement_lines for line-level tracking
ALTER TABLE settlement_lines ADD COLUMN order_item_id BIGINT DEFAULT NULL AFTER partner_order_id;

-- 12. Create partner_order_audit table for transition audit trail
CREATE TABLE IF NOT EXISTS partner_order_audit (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_order_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    actor_user_id BIGINT,
    from_status VARCHAR(30),
    to_status VARCHAR(30),
    reason VARCHAR(1000),
    idempotency_key VARCHAR(255),
    correlation_id VARCHAR(100),
    occurred_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    INDEX idx_po_audit_order (partner_order_id),
    INDEX idx_po_audit_partner (partner_id),
    CONSTRAINT fk_po_audit_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders(id),
    CONSTRAINT fk_po_audit_partner FOREIGN KEY (partner_id) REFERENCES partners(id)
) ENGINE=InnoDB;
