#!/bin/bash
set -e

echo "=== Starting infrastructure ==="
cd "$(dirname "$0")"

docker compose up -d 2>/dev/null || docker-compose up -d

echo "=== Waiting for Kafka ==="
for i in $(seq 1 30); do
  if docker compose ps kafka 2>/dev/null | grep -q "Up" || docker-compose ps kafka 2>/dev/null | grep -q "Up"; then
    echo "Kafka ready"
    break
  fi
  sleep 2
done

echo "=== Ensuring database ==="
docker exec tsm-mysql mysql -u root -ppassword \
  -e "CREATE DATABASE IF NOT EXISTS ecommerce_platform CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;" 2>/dev/null

docker exec tsm-mysql mysql -u root -ppassword \
  -e "CREATE TABLE IF NOT EXISTS ecommerce_platform.outbox_events (
    id BIGINT AUTO_INCREMENT PRIMARY KEY, event_id CHAR(36) NOT NULL UNIQUE,
    aggregate_type VARCHAR(50) NOT NULL, aggregate_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL, event_version INT NOT NULL,
    topic VARCHAR(255) NOT NULL, event_key VARCHAR(255) NOT NULL,
    payload JSON NOT NULL, idempotency_key VARCHAR(150) NOT NULL UNIQUE,
    status VARCHAR(20) NOT NULL, retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 10, next_retry_at DATETIME(6),
    claimed_by VARCHAR(100), claimed_at DATETIME(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    published_at DATETIME(6), last_error VARCHAR(1000),
    INDEX idx_outbox_claim (status, next_retry_at, id)
  ) ENGINE=InnoDB;" 2>/dev/null

echo "=== Starting server ==="
export JWT_SECRET="${JWT_SECRET:-$(grep '^export JWT_SECRET=' ~/.bashrc | cut -d'"' -f2)}"
export DB_PASSWORD="${DB_PASSWORD:-password}"

mvn -pl ecommerce-platform-server -am spring-boot:run -DskipTests
