-- 1. Extend inventory_reservations for partner flow
ALTER TABLE inventory_reservations ADD inventory_source_type VARCHAR(20) NOT NULL DEFAULT 'PRODUCT';
ALTER TABLE inventory_reservations ADD offer_id BIGINT NOT NULL DEFAULT 0;
ALTER TABLE inventory_reservations ADD partner_order_id BIGINT NULL;

-- 2. Add partner_order_id + snapshot columns to order_items
ALTER TABLE order_items ADD partner_order_id BIGINT DEFAULT NULL;
ALTER TABLE order_items ADD discount_allocation DECIMAL(19,2) DEFAULT 0.00;
ALTER TABLE order_items ADD shipping_allocation DECIMAL(19,2) DEFAULT 0.00;
ALTER TABLE order_items ADD partner_name VARCHAR(255) DEFAULT NULL;
CREATE INDEX idx_order_item_partner_order ON order_items (partner_order_id);

-- 3. Add AWAITING_PAYMENT to partner_orders status check
ALTER TABLE partner_orders DROP CONSTRAINT IF EXISTS chk_partner_order_status;
ALTER TABLE partner_orders
    ADD CONSTRAINT chk_partner_order_status CHECK (status IN ('AWAITING_PAYMENT','NEW','ACCEPTED','REJECTED','PACKING','READY_TO_SHIP','SHIPPED','DELIVERED','CANCELLED','RETURN_REQUESTED','RETURNED'));

-- 4. Add settlement linkage on partner_orders
ALTER TABLE partner_orders ADD settlement_id BIGINT DEFAULT NULL;
ALTER TABLE partner_orders ADD settlement_status VARCHAR(20) DEFAULT 'UNSETTLED';
CREATE INDEX idx_partner_order_settlement ON partner_orders (settlement_id);

-- 5. Add unique constraint on settlements for idempotent calculation
CREATE UNIQUE INDEX uk_settlement_period_partner ON settlements (partner_id, period_start, period_end, currency);

-- 6. Add UNIQUE(cart_id, product_id, offer_id) on cart_items
UPDATE cart_items SET offer_id = 0 WHERE offer_id IS NULL;
CREATE UNIQUE INDEX uk_cart_item_product_offer ON cart_items (cart_id, product_id, offer_id);
