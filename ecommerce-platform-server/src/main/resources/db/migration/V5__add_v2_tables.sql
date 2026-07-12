CREATE TABLE processed_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    consumer_name VARCHAR(255) NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    correlation_id VARCHAR(255),
    aggregate_id BIGINT,
    processed_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_consumer_event (consumer_name, event_id),
    KEY idx_processed_at (processed_at)
) ENGINE=InnoDB;

CREATE TABLE refunds (
    id BIGINT NOT NULL AUTO_INCREMENT,
    order_id BIGINT NOT NULL,
    payment_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    reason VARCHAR(500),
    requested_by BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    request_idempotency_key VARCHAR(255) NOT NULL,
    external_refund_id VARCHAR(255),
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_request_idempotency_key (request_idempotency_key),
    KEY idx_order_id (order_id),
    KEY idx_status (status)
) ENGINE=InnoDB;

CREATE TABLE operations_alerts (
    id BIGINT NOT NULL AUTO_INCREMENT,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    alert_type VARCHAR(100) NOT NULL,
    severity VARCHAR(50) NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'OPEN',
    details_redacted TEXT,
    idempotency_key VARCHAR(255) NOT NULL,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_idempotency_key (idempotency_key)
) ENGINE=InnoDB;
