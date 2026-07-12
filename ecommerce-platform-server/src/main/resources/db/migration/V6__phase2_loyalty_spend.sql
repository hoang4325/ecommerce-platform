CREATE TABLE spend_ledger (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    refund_id BIGINT,
    amount DECIMAL(19,2) NOT NULL,
    currency CHAR(3) NOT NULL,
    transaction_type VARCHAR(30) NOT NULL,
    transaction_date DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    external_reference VARCHAR(255),
    idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    CONSTRAINT fk_spend_user FOREIGN KEY (user_id) REFERENCES users(id),
    CONSTRAINT fk_spend_order FOREIGN KEY (order_id) REFERENCES orders(id),
    INDEX idx_spend_user_date (user_id, transaction_date)
) ENGINE=InnoDB;

ALTER TABLE point_lots
    ADD COLUMN created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    ADD UNIQUE KEY uk_point_lot_source_order_type (source_order_id, lot_type);
