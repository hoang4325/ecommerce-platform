CREATE TABLE partner_order_commands (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    partner_order_id BIGINT NOT NULL,
    partner_id BIGINT NOT NULL,
    command_type VARCHAR(50) NOT NULL,
    idempotency_key VARCHAR(255) NOT NULL,
    request_hash VARCHAR(64) NOT NULL,
    response_snapshot TEXT,
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    executed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_partner_order_idempotency_key UNIQUE (idempotency_key, partner_order_id),
    CONSTRAINT fk_poc_partner_order FOREIGN KEY (partner_order_id) REFERENCES partner_orders(id),
    INDEX idx_poc_status (status),
    INDEX idx_poc_partner_order_id (partner_order_id)
);
