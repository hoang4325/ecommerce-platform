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

ALTER TABLE notifications
    ADD COLUMN event_id VARCHAR(36),
    ADD COLUMN business_idempotency_key VARCHAR(255),
    ADD COLUMN claimed_by VARCHAR(255),
    ADD COLUMN claimed_at DATETIME;

CREATE INDEX idx_notifications_event_id ON notifications(event_id);
CREATE UNIQUE INDEX uk_notifications_business_key ON notifications(business_idempotency_key);
CREATE INDEX idx_notifications_status_claimed ON notifications(status, claimed_by, claimed_at);
