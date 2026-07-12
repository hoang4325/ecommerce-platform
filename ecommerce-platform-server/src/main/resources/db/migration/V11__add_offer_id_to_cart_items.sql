ALTER TABLE cart_items
    ADD COLUMN offer_id BIGINT NULL AFTER product_id,
    ADD COLUMN partner_id BIGINT NULL AFTER offer_id;

ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_items_offer_id FOREIGN KEY (offer_id) REFERENCES partner_offers(id);

ALTER TABLE cart_items
    ADD CONSTRAINT fk_cart_items_partner_id FOREIGN KEY (partner_id) REFERENCES partners(id);
