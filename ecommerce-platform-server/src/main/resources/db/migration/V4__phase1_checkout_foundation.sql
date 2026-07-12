ALTER TABLE products
    MODIFY price DECIMAL(19,2) NOT NULL,
    ADD COLUMN on_hand_quantity INT NOT NULL DEFAULT 0,
    ADD COLUMN reserved_quantity INT NOT NULL DEFAULT 0,
    ADD COLUMN active BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE cart_items MODIFY price DECIMAL(19,2) NOT NULL;

ALTER TABLE orders
    ADD COLUMN subtotal DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN product_discount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN order_discount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN coupon_discount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN redeemed_point_value DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN shipping_fee DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'EUR',
    ADD COLUMN reservation_expires_at DATETIME(6),
    ADD COLUMN payment_deadline DATETIME(6),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0;

ALTER TABLE payments
    ADD COLUMN currency CHAR(3) NOT NULL DEFAULT 'EUR',
    ADD COLUMN external_payment_id VARCHAR(255),
    ADD COLUMN version BIGINT NOT NULL DEFAULT 0,
    ADD UNIQUE KEY uk_payment_order (order_id),
    ADD UNIQUE KEY uk_payment_external (external_payment_id);

CREATE TABLE order_items (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    order_id BIGINT NOT NULL,
    product_id BIGINT,
    name VARCHAR(255) NOT NULL,
    unit_price DECIMAL(19,2) NOT NULL,
    quantity INT NOT NULL,
    product_discount DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    order_discount_allocation DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    coupon_discount_allocation DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    redeemed_point_allocation DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    line_total DECIMAL(19,2) NOT NULL,
    qualifying_amount DECIMAL(19,2) NOT NULL,
    is_gift BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_order_item_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT fk_order_item_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT chk_order_item_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE checkout_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    order_id BIGINT,
    response_snapshot JSON,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_checkout_user_key (user_id, idempotency_key),
    CONSTRAINT fk_checkout_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_checkout_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

CREATE TABLE payment_initiation_requests (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    idempotency_key CHAR(36) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    response_snapshot JSON,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_initiation_user_key (user_id, idempotency_key),
    UNIQUE KEY uk_initiation_payment (payment_id),
    CONSTRAINT fk_initiation_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_initiation_payment FOREIGN KEY (payment_id) REFERENCES payments(id)
) ENGINE=InnoDB;

CREATE TABLE inventory_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, product_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
    quantity INT NOT NULL, status VARCHAR(20) NOT NULL, expires_at DATETIME(6) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6), updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_inventory_order_product (order_id, product_id), UNIQUE KEY uk_inventory_key (idempotency_key),
    CONSTRAINT fk_inventory_product FOREIGN KEY (product_id) REFERENCES products(id),
    CONSTRAINT fk_inventory_order FOREIGN KEY (order_id) REFERENCES orders(id),
    CONSTRAINT chk_inventory_quantity CHECK (quantity > 0)
) ENGINE=InnoDB;

CREATE TABLE promotions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE,
    discount_amount DECIMAL(19,2) NOT NULL DEFAULT 0.00, currency CHAR(3) NOT NULL,
    status VARCHAR(20) NOT NULL, start_at DATETIME(6) NOT NULL, end_at DATETIME(6) NOT NULL,
    remaining_usage INT, per_customer_limit INT, version BIGINT NOT NULL DEFAULT 0
) ENGINE=InnoDB;

CREATE TABLE promotion_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, promotion_id BIGINT NOT NULL, user_id BIGINT NOT NULL, order_id BIGINT NOT NULL,
    applications INT NOT NULL DEFAULT 1, discount_amount DECIMAL(19,2) NOT NULL, status VARCHAR(20) NOT NULL,
    expires_at DATETIME(6) NOT NULL, idempotency_key VARCHAR(100) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_promotion_order (promotion_id, order_id), UNIQUE KEY uk_promotion_reservation_key (idempotency_key),
    CONSTRAINT fk_pr_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id),
    CONSTRAINT fk_pr_user FOREIGN KEY (user_id) REFERENCES users(id), CONSTRAINT fk_pr_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

CREATE TABLE promotion_usage_counters (
    promotion_id BIGINT NOT NULL, user_id BIGINT NOT NULL, reserved_orders INT NOT NULL DEFAULT 0,
    consumed_orders INT NOT NULL DEFAULT 0, version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (promotion_id, user_id), CONSTRAINT fk_puc_promotion FOREIGN KEY (promotion_id) REFERENCES promotions(id),
    CONSTRAINT fk_puc_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE loyalty_accounts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, user_id BIGINT NOT NULL UNIQUE, available_points INT NOT NULL DEFAULT 0,
    reserved_points INT NOT NULL DEFAULT 0, lifetime_points INT NOT NULL DEFAULT 0, loyalty_debt INT NOT NULL DEFAULT 0,
    membership_tier VARCHAR(20) NOT NULL DEFAULT 'MEMBER', version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_loyalty_user FOREIGN KEY (user_id) REFERENCES users(id)
) ENGINE=InnoDB;

CREATE TABLE point_lots (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL, source_order_id BIGINT,
    original_points INT NOT NULL, remaining_points INT NOT NULL, expires_at DATETIME(6) NOT NULL,
    lot_type VARCHAR(20) NOT NULL, version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_lot_account FOREIGN KEY (account_id) REFERENCES loyalty_accounts(id),
    INDEX idx_lot_fifo (account_id, expires_at, id)
) ENGINE=InnoDB;

CREATE TABLE point_reservations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL, order_id BIGINT NOT NULL UNIQUE,
    total_points INT NOT NULL, total_value DECIMAL(19,2) NOT NULL, currency CHAR(3) NOT NULL,
    status VARCHAR(30) NOT NULL, expires_at DATETIME(6) NOT NULL, idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    version BIGINT NOT NULL DEFAULT 0, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_point_res_account FOREIGN KEY (account_id) REFERENCES loyalty_accounts(id),
    CONSTRAINT fk_point_res_order FOREIGN KEY (order_id) REFERENCES orders(id)
) ENGINE=InnoDB;

CREATE TABLE point_reservation_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, reservation_id BIGINT NOT NULL, point_lot_id BIGINT NOT NULL,
    reserved_points INT NOT NULL, created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    UNIQUE KEY uk_point_allocation (reservation_id, point_lot_id),
    CONSTRAINT fk_allocation_res FOREIGN KEY (reservation_id) REFERENCES point_reservations(id),
    CONSTRAINT fk_allocation_lot FOREIGN KEY (point_lot_id) REFERENCES point_lots(id),
    CONSTRAINT chk_reserved_points CHECK (reserved_points > 0)
) ENGINE=InnoDB;

CREATE TABLE loyalty_transactions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, account_id BIGINT NOT NULL, order_id BIGINT, reservation_id BIGINT,
    point_lot_id BIGINT, transaction_type VARCHAR(30) NOT NULL, points INT NOT NULL,
    value DECIMAL(19,2) NOT NULL DEFAULT 0.00, currency CHAR(3) NOT NULL,
    balance_after INT NOT NULL, idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_lt_account FOREIGN KEY (account_id) REFERENCES loyalty_accounts(id)
) ENGINE=InnoDB;

CREATE TABLE outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id CHAR(36) NOT NULL UNIQUE, aggregate_type VARCHAR(50) NOT NULL,
    aggregate_id VARCHAR(100) NOT NULL, event_type VARCHAR(100) NOT NULL, event_version INT NOT NULL,
    topic VARCHAR(255) NOT NULL, event_key VARCHAR(255) NOT NULL, payload JSON NOT NULL,
    idempotency_key VARCHAR(150) NOT NULL UNIQUE, status VARCHAR(20) NOT NULL,
    retry_count INT NOT NULL DEFAULT 0, max_retries INT NOT NULL DEFAULT 10, next_retry_at DATETIME(6),
    claimed_by VARCHAR(100), claimed_at DATETIME(6), created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6), last_error VARCHAR(1000), INDEX idx_outbox_claim (status, next_retry_at, id)
) ENGINE=InnoDB;
