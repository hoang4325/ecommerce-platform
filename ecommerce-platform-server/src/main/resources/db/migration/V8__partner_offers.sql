CREATE TABLE partner_offers (
    id BIGINT NOT NULL AUTO_INCREMENT,
    partner_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    partner_sku VARCHAR(100),
    price DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    on_hand_quantity INT NOT NULL DEFAULT 0,
    reserved_quantity INT NOT NULL DEFAULT 0,
    status VARCHAR(30) NOT NULL DEFAULT 'DRAFT',
    submitted_at DATETIME(6),
    approved_at DATETIME(6),
    approved_by BIGINT,
    rejection_reason VARCHAR(1000),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_offer_partner_sku (partner_id, partner_sku),
    KEY idx_offer_partner (partner_id),
    KEY idx_offer_product (product_id),
    KEY idx_offer_status (status),
    CONSTRAINT fk_offer_partner FOREIGN KEY (partner_id) REFERENCES partners(id),
    CONSTRAINT fk_offer_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_offer_approved_by FOREIGN KEY (approved_by) REFERENCES users(id),
    CONSTRAINT chk_offer_price CHECK (price > 0),
    CONSTRAINT chk_offer_on_hand CHECK (on_hand_quantity >= 0),
    CONSTRAINT chk_offer_reserved CHECK (reserved_quantity >= 0),
    CONSTRAINT chk_offer_status CHECK (status IN ('DRAFT','PENDING_REVIEW','APPROVED','REJECTED','SUSPENDED','OUT_OF_STOCK','ARCHIVED'))
) ENGINE=InnoDB;

INSERT INTO partner_offers (partner_id, product_id, partner_sku, price, currency, on_hand_quantity, reserved_quantity, status, approved_at, version, created_at, updated_at)
SELECT p.id, pr.id, CONCAT('LEGACY-', pr.id), pr.price, 'USD', pr.on_hand_quantity, pr.reserved_quantity, 'APPROVED', CURRENT_TIMESTAMP(6), 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)
FROM products pr
JOIN partners p ON p.applicant_user_id = pr.user_id
WHERE NOT EXISTS (SELECT 1 FROM partner_offers o WHERE o.product_id = pr.id);
