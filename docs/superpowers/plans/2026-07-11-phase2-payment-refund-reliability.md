# Phase 2 — Payment/Refund Reliability Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development or superpowers:dispatching-parallel-agents. Work streams can run in parallel. Steps use checkbox (`- [ ]`) syntax.

**Goal:** Implement reliable outbox/inbox/worker patterns for payment and refund flows across Main Server, Payment Service, and Notification Service, with V2 event contracts, UNKNOWN reconciliation, and idempotency.

**Architecture:** Each service gets a local outbox (claim→send→result transaction), inbox (processed_events + business write in one transaction), and worker (lease→Stripe→terminal result outside DB). Refund flow mirrors payment flow. UNKNOWN is reconciled (not treated as FAILED). V1 coexists during grace.

**Tech Stack:** Spring Boot 3.0.4, Java 17, Flyway, Kafka, Stripe SDK, MySQL 8, JPA

## Global Constraints

- No dual production for one business operation — Main selects V1 or V2 by persisted `protocol_version`.
- Payment Service only emits terminal result events (SUCCEEDED/FAILED), not UNKNOWN.
- UNKNOWN is reconciled in Payment Service; `PaymentReviewRequiredEventV2` alerts Main if needed.
- All status updates use `@Version`/optimistic locking + expected status guard.
- Controller never sets status; only service with version guard does.
- Stripe calls happen OUTSIDE DB transaction with stable idempotency key.
- `processed_events` insert + business write in same DB transaction; unique `(consumer_name, event_id)`.
- PaymentMethod ID never logged, echoed in response, or in audit trail.
- Full refund only from PAID/SUCCEEDED. No auto-restock on refund success.
- Deduplication keys: `payment-init:<paymentId>`, `payment-result:<paymentId>:SUCCEEDED`, `refund-request:<refundId>`, `refund-result:<refundId>:<status>`.
- V2 consumer must coexist with V1 during grace period (dual-consumption).

---
---

# Work Stream A: Payment Service V2

## Task A1: V2 Event Classes for Payment Service

**Files:**
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/events/PaymentRequestedEventV2.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/events/PaymentResultEventV2.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/events/PaymentReviewRequiredEventV2.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/events/RefundRequestedEventV2.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/events/RefundResultEventV2.java`

**Interfaces:**
- Produces: `PaymentRequestedEventV2` (inbound), `PaymentResultEventV2` (outbound), `PaymentReviewRequiredEventV2` (outbound), `RefundRequestedEventV2` (inbound), `RefundResultEventV2` (outbound)

- [ ] **Step 1: Create `PaymentRequestedEventV2.java`**

```java
package com.yashmerino.ecommerce.kafka.events;

import java.math.BigDecimal;

public record PaymentRequestedEventV2(
    String eventId,
    String eventType,
    int eventVersion,
    String occurredAt,
    String correlationId,
    Long aggregateId,
    String producer,
    String idempotencyKey,
    Long paymentId,
    Long orderId,
    String amount,
    String currency,
    String paymentMethodRef
) {
    public PaymentRequestedEventV2 {
        eventVersion = 2;
    }
}
```

- [ ] **Step 2: Create `PaymentResultEventV2.java`**

```java
package com.yashmerino.ecommerce.kafka.events;

import java.math.BigDecimal;

public record PaymentResultEventV2(
    String eventId,
    String eventType,
    int eventVersion,
    String occurredAt,
    String correlationId,
    Long aggregateId,
    String producer,
    String idempotencyKey,
    Long paymentId,
    Long orderId,
    String amount,
    String currency,
    String externalPaymentId,
    String status,
    String failureCode,
    String failureMessage
) {
    public PaymentResultEventV2 {
        eventVersion = 2;
    }
}
```

- [ ] **Step 3: Create `PaymentReviewRequiredEventV2.java`**

```java
package com.yashmerino.ecommerce.kafka.events;

public record PaymentReviewRequiredEventV2(
    String eventId,
    String eventType,
    int eventVersion,
    String occurredAt,
    String correlationId,
    Long aggregateId,
    String producer,
    String idempotencyKey,
    Long paymentId,
    Long orderId,
    String externalPaymentId,
    String reasonCode,
    String redactedDetail,
    String observedAt
) {
    public PaymentReviewRequiredEventV2 {
        eventVersion = 2;
    }
}
```

- [ ] **Step 4: Create `RefundRequestedEventV2.java`**

```java
package com.yashmerino.ecommerce.kafka.events;

public record RefundRequestedEventV2(
    String eventId,
    String eventType,
    int eventVersion,
    String occurredAt,
    String correlationId,
    Long aggregateId,
    String producer,
    String idempotencyKey,
    Long refundId,
    Long orderId,
    Long paymentId,
    String externalPaymentId,
    String amount,
    String currency,
    String reason,
    String requestedBy,
    String requestIdempotencyKey
) {
    public RefundRequestedEventV2 {
        eventVersion = 2;
    }
}
```

- [ ] **Step 5: Create `RefundResultEventV2.java`**

```java
package com.yashmerino.ecommerce.kafka.events;

public record RefundResultEventV2(
    String eventId,
    String eventType,
    int eventVersion,
    String occurredAt,
    String correlationId,
    Long aggregateId,
    String producer,
    String idempotencyKey,
    Long refundId,
    Long orderId,
    Long paymentId,
    String externalPaymentId,
    String stripeRefundId,
    String amount,
    String currency,
    String status,
    String failureCode,
    String failureMessage
) {
    public RefundResultEventV2 {
        eventVersion = 2;
    }
}
```

- [ ] **Step 6: Run compile check**

Run: `cd ecommerce-platform-payment-service && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/events/
git commit -m "feat(payment): add V2 event contracts"
```

---

## Task A2: Payment Service V2 Database Migrations

**Files:**
- Create: `ecommerce-platform-payment-service/src/main/resources/db/migration/V2__add_v2_tables.sql`

**Interfaces:**
- Consumes: Task A1 (event classes)
- Produces: `payment_operations` table, `refund_operations` table, `outbox_events` table, `processed_events` table

- [ ] **Step 1: Create V2 migration**

```sql
CREATE TABLE payment_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    main_payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    payment_method_ref VARCHAR(255),
    request_idempotency_key VARCHAR(255) NOT NULL,
    stripe_idempotency_key VARCHAR(255),
    stripe_payment_intent_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    claimed_by VARCHAR(255),
    claimed_at DATETIME,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_main_payment_id (main_payment_id),
    UNIQUE KEY uk_request_idempotency_key (request_idempotency_key),
    UNIQUE KEY uk_stripe_idempotency_key (stripe_idempotency_key),
    UNIQUE KEY uk_stripe_payment_intent_id (stripe_payment_intent_id),
    KEY idx_status_claimed (status, claimed_by, claimed_at)
) ENGINE=InnoDB;

CREATE TABLE refund_operations (
    id BIGINT NOT NULL AUTO_INCREMENT,
    refund_id BIGINT NOT NULL,
    main_payment_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    amount DECIMAL(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL,
    external_payment_id VARCHAR(255),
    stripe_refund_id VARCHAR(255),
    request_idempotency_key VARCHAR(255) NOT NULL,
    stripe_idempotency_key VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'RECEIVED',
    failure_code VARCHAR(100),
    failure_message VARCHAR(500),
    claimed_by VARCHAR(255),
    claimed_at DATETIME,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME NOT NULL,
    updated_at DATETIME NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_refund_id (refund_id),
    UNIQUE KEY uk_request_idempotency_key (request_idempotency_key),
    UNIQUE KEY uk_stripe_idempotency_key (stripe_idempotency_key),
    UNIQUE KEY uk_stripe_refund_id (stripe_refund_id),
    KEY idx_status_claimed (status, claimed_by, claimed_at)
) ENGINE=InnoDB;

CREATE TABLE outbox_events (
    id BIGINT NOT NULL AUTO_INCREMENT,
    event_id VARCHAR(36) NOT NULL,
    aggregate_type VARCHAR(100) NOT NULL,
    aggregate_id BIGINT NOT NULL,
    event_type VARCHAR(100) NOT NULL,
    event_version INT NOT NULL DEFAULT 2,
    topic VARCHAR(255) NOT NULL,
    event_key VARCHAR(255),
    payload JSON NOT NULL,
    idempotency_key VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    retry_count INT NOT NULL DEFAULT 0,
    max_retries INT NOT NULL DEFAULT 5,
    next_retry_at DATETIME,
    claimed_by VARCHAR(255),
    claimed_at DATETIME,
    created_at DATETIME NOT NULL,
    published_at DATETIME,
    last_error VARCHAR(500),
    PRIMARY KEY (id),
    UNIQUE KEY uk_event_id (event_id),
    UNIQUE KEY uk_idempotency_key (idempotency_key),
    KEY idx_status_retry (status, next_retry_at, retry_count),
    KEY idx_status_claimed (status, claimed_by, claimed_at)
) ENGINE=InnoDB;

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
```

- [ ] **Step 2: Add V2 topic properties to `application.properties`**

Add at end:
```properties
payment.topics.payment-requested-v2=payment.requested.v2
payment.topics.payment-result-v2=payment.result.v2
payment.topics.payment-review-v2=payment.review.required.v2
payment.topics.refund-requested-v2=payment.refund.requested.v2
payment.topics.refund-result-v2=payment.refund.result.v2
payment.topics.notification-requested-v2=notification.requested.v2
```

- [ ] **Step 3: Commit**
```bash
git add ecommerce-platform-payment-service/src/main/resources/db/migration/V2__add_v2_tables.sql
git add ecommerce-platform-payment-service/src/main/resources/application.properties
git commit -m "feat(payment): add V2 database tables and topic config"
```

---

## Task A3: Payment Service Outbox Infrastructure

**Files:**
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/outbox/OutboxEvent.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/repository/OutboxEventRepository.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/OutboxPublisher.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/config/OutboxConfig.java`

**Interfaces:**
- Consumes: Task A2 (outbox_events table)
- Produces: `OutboxPublisher.publish(eventId, aggregateType, aggregateId, eventType, topic, eventKey, payload, idempotencyKey)`, `OutboxPublisher.claimAndPublish()`

- [ ] **Step 1: Create `OutboxEvent.java` entity**

```java
package com.yashmerino.ecommerce.model.outbox;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
public class OutboxEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 100)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    private Long aggregateId;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "event_version", nullable = false)
    private int eventVersion = 2;

    @Column(nullable = false, length = 255)
    private String topic;

    @Column(name = "event_key", length = 255)
    private String eventKey;

    @Column(nullable = false, columnDefinition = "JSON")
    private String payload;

    @Column(name = "idempotency_key", length = 255)
    private String idempotencyKey;

    @Column(nullable = false, length = 50)
    private String status = "PENDING";

    @Column(name = "retry_count", nullable = false)
    private int retryCount = 0;

    @Column(name = "max_retries", nullable = false)
    private int maxRetries = 5;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "claimed_by", length = 255)
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    public OutboxEvent() {}

    public OutboxEvent(String eventId, String aggregateType, Long aggregateId, String eventType,
                       String topic, String eventKey, String payload, String idempotencyKey) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.eventVersion = 2;
        this.topic = topic;
        this.eventKey = eventKey;
        this.payload = payload;
        this.idempotencyKey = idempotencyKey;
        this.status = "PENDING";
        this.retryCount = 0;
        this.maxRetries = 5;
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public String getEventId() { return eventId; }
    public String getTopic() { return topic; }
    public String getEventKey() { return eventKey; }
    public String getPayload() { return payload; }
    public String getStatus() { return status; }
    public int getRetryCount() { return retryCount; }
    public int getMaxRetries() { return maxRetries; }
    public LocalDateTime getNextRetryAt() { return nextRetryAt; }
    public String getClaimedBy() { return claimedBy; }
    public LocalDateTime getClaimedAt() { return claimedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getLastError() { return lastError; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getEventType() { return eventType; }
    public String getAggregateType() { return aggregateType; }
    public Long getAggregateId() { return aggregateId; }
    public int getEventVersion() { return eventVersion; }

    public void markProcessing(String claimer) {
        this.status = "PROCESSING";
        this.claimedBy = claimer;
        this.claimedAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.status = "PUBLISHED";
        this.publishedAt = LocalDateTime.now();
        this.claimedBy = null;
        this.claimedAt = null;
    }

    public void markRetry(String error) {
        this.retryCount++;
        this.lastError = error;
        this.status = this.retryCount >= this.maxRetries ? "DEAD_LETTER" : "RETRY";
        this.nextRetryAt = LocalDateTime.now().plusSeconds((long) Math.pow(2, this.retryCount));
        this.claimedBy = null;
        this.claimedAt = null;
    }
}
```

- [ ] **Step 2: Create `OutboxEventRepository.java`**

```java
package com.yashmerino.ecommerce.repository;

import com.yashmerino.ecommerce.model.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = """
        SELECT * FROM outbox_events
        WHERE status IN ('PENDING', 'RETRY')
          AND (next_retry_at IS NULL OR next_retry_at <= :now)
          AND (claimed_at IS NULL OR claimed_at < :leaseExpiry)
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<OutboxEvent> claimPendingEvents(@Param("now") LocalDateTime now,
                                         @Param("leaseExpiry") LocalDateTime leaseExpiry,
                                         @Param("limit") int limit);
}
```

- [ ] **Step 3: Create `OutboxConfig.java`**

```java
package com.yashmerino.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

    @Value("${outbox.poll-size:10}")
    private int pollSize;

    @Value("${outbox.lease-seconds:30}")
    private int leaseSeconds;

    @Value("${outbox.publisher-id:payment-service-${random.value}}")
    private String publisherId;

    public int getPollSize() { return pollSize; }
    public int getLeaseSeconds() { return leaseSeconds; }
    public String getPublisherId() { return publisherId; }
}
```

- [ ] **Step 4: Create `OutboxPublisher.java`**

```java
package com.yashmerino.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.config.OutboxConfig;
import com.yashmerino.ecommerce.model.outbox.OutboxEvent;
import com.yashmerino.ecommerce.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final OutboxConfig config;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(OutboxEventRepository repository,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           OutboxConfig config,
                           TransactionTemplate transactionTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.config = config;
        this.transactionTemplate = transactionTemplate;
    }

    public void enqueue(String eventId, String aggregateType, Long aggregateId,
                        String eventType, String topic, String eventKey,
                        Object payload, String idempotencyKey) {
        try {
            String json = objectMapper.writeValueAsString(payload);
            OutboxEvent event = new OutboxEvent(eventId, aggregateType, aggregateId,
                eventType, topic, eventKey, json, idempotencyKey);
            repository.save(event);
        } catch (Exception e) {
            throw new RuntimeException("Failed to enqueue outbox event", e);
        }
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiry = now.minusSeconds(config.getLeaseSeconds());
        List<OutboxEvent> events = repository.claimPendingEvents(now, leaseExpiry, config.getPollSize());

        for (OutboxEvent event : events) {
            event.markProcessing(config.getPublisherId());
            repository.save(event);

            try {
                CompletableFuture<?> future = kafkaTemplate.send(
                    event.getTopic(), event.getEventKey(), event.getPayload()
                );
                future.get(10, TimeUnit.SECONDS);

                event.markPublished();
                repository.save(event);
                log.debug("Published outbox event {} to topic {}", event.getEventId(), event.getTopic());
            } catch (Exception e) {
                event.markRetry(e.getMessage());
                repository.save(event);
                log.warn("Failed to publish outbox event {}: {}", event.getEventId(), e.getMessage());
            }
        }
    }
}
```

- [ ] **Step 5: Add scheduling and transaction template config to `application.properties`**

```properties
outbox.poll-interval-ms=2000
outbox.poll-size=10
outbox.lease-seconds=30
```

Also add to main class:
```java
@SpringBootApplication
@EnableScheduling
public class EcommercePaymentApplication { ... }
```

- [ ] **Step 6: Compile check**

Run: `cd ecommerce-platform-payment-service && mvn compile -q 2>&1 | tail -5`
Expected: BUILD SUCCESS

- [ ] **Step 7: Commit**

```bash
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/outbox/
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/repository/OutboxEventRepository.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/OutboxPublisher.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/config/OutboxConfig.java
git add ecommerce-platform-payment-service/src/main/resources/application.properties
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/EcommercePaymentApplication.java
git commit -m "feat(payment): add outbox publisher with claim/send/result pattern"
```

---

## Task A4: Payment Service Inbox/Processed Events Infrastructure

**Files:**
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/processed/ProcessedEvent.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/repository/ProcessedEventRepository.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/InboxService.java`

**Interfaces:**
- Consumes: Task A2 (processed_events table)
- Produces: `InboxService.isProcessed(consumerName, eventId)`, `InboxService.markProcessed(consumerName, eventId, eventType, correlationId, aggregateId)`

- [ ] **Step 1: Create `ProcessedEvent.java` entity**

```java
package com.yashmerino.ecommerce.model.processed;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events",
       uniqueConstraints = @UniqueConstraint(columnNames = {"consumer_name", "event_id"}))
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "consumer_name", nullable = false, length = 255)
    private String consumerName;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent() {}

    public ProcessedEvent(String eventId, String consumerName, String eventType,
                          String correlationId, Long aggregateId) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.aggregateId = aggregateId;
        this.processedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create `ProcessedEventRepository.java`**

```java
package com.yashmerino.ecommerce.repository;

import com.yashmerino.ecommerce.model.processed.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {

    boolean existsByConsumerNameAndEventId(String consumerName, String eventId);
}
```

- [ ] **Step 3: Create `InboxService.java`**

```java
package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.model.processed.ProcessedEvent;
import com.yashmerino.ecommerce.repository.ProcessedEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class InboxService {

    private final ProcessedEventRepository processedEventRepository;

    public InboxService(ProcessedEventRepository processedEventRepository) {
        this.processedEventRepository = processedEventRepository;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isAlreadyProcessed(String consumerName, String eventId) {
        return processedEventRepository.existsByConsumerNameAndEventId(consumerName, eventId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(String consumerName, String eventId, String eventType,
                              String correlationId, Long aggregateId) {
        ProcessedEvent event = new ProcessedEvent(eventId, consumerName, eventType,
            correlationId, aggregateId);
        processedEventRepository.save(event);
    }
}
```

- [ ] **Step 4: Compile check**

```bash
cd ecommerce-platform-payment-service && mvn compile -q 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/processed/
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/repository/ProcessedEventRepository.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/InboxService.java
git commit -m "feat(payment): add inbox/processed-events infrastructure"
```

---

## Task A5: Payment Service V2 Consumer + Worker (Payment Flow)

**Files:**
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/operations/PaymentOperation.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/repository/PaymentOperationRepository.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/PaymentWorker.java`
- Modify: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/PaymentEventListener.java` (add V2 listener)

**Interfaces:**
- Consumes: Task A1 (V2 events), Task A3 (OutboxPublisher), Task A4 (InboxService), Task A2 (payment_operations table)
- Produces: V2 PaymentResultEventV2 via OutboxPublisher, PaymentReviewRequiredEventV2 via OutboxPublisher

- [ ] **Step 1: Create `PaymentOperation.java` entity**

```java
package com.yashmerino.ecommerce.model.operations;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_operations")
public class PaymentOperation {

    public enum Status {
        RECEIVED, PROCESSING, SUCCEEDED, FAILED, UNKNOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "main_payment_id", nullable = false)
    private Long mainPaymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method_ref", length = 255)
    private String paymentMethodRef;

    @Column(name = "request_idempotency_key", nullable = false, length = 255)
    private String requestIdempotencyKey;

    @Column(name = "stripe_idempotency_key", length = 255)
    private String stripeIdempotencyKey;

    @Column(name = "stripe_payment_intent_id", length = 255)
    private String stripePaymentIntentId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.RECEIVED;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "claimed_by", length = 255)
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public PaymentOperation() {}

    public PaymentOperation(Long mainPaymentId, Long orderId, BigDecimal amount,
                            String currency, String paymentMethodRef,
                            String requestIdempotencyKey) {
        this.mainPaymentId = mainPaymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.paymentMethodRef = paymentMethodRef;
        this.requestIdempotencyKey = requestIdempotencyKey;
        this.status = Status.RECEIVED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getMainPaymentId() { return mainPaymentId; }
    public Long getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getPaymentMethodRef() { return paymentMethodRef; }
    public String getRequestIdempotencyKey() { return requestIdempotencyKey; }
    public String getStripeIdempotencyKey() { return stripeIdempotencyKey; }
    public String getStripePaymentIntentId() { return stripePaymentIntentId; }
    public Status getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getClaimedBy() { return claimedBy; }
    public LocalDateTime getClaimedAt() { return claimedAt; }

    public void assignStripeIdempotencyKey(String key) {
        this.stripeIdempotencyKey = key;
    }

    public void markProcessing(String claimer) {
        this.status = Status.PROCESSING;
        this.claimedBy = claimer;
        this.claimedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markSucceeded(String stripePaymentIntentId) {
        this.status = Status.SUCCEEDED;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String failureCode, String failureMessage) {
        this.status = Status.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void markUnknown() {
        this.status = Status.UNKNOWN;
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create `PaymentOperationRepository.java`**

```java
package com.yashmerino.ecommerce.repository;

import com.yashmerino.ecommerce.model.operations.PaymentOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PaymentOperationRepository extends JpaRepository<PaymentOperation, Long> {

    Optional<PaymentOperation> findByMainPaymentId(Long mainPaymentId);

    boolean existsByMainPaymentId(Long mainPaymentId);

    @Query(value = """
        SELECT * FROM payment_operations
        WHERE status = 'RECEIVED'
          AND (claimed_at IS NULL OR claimed_at < :leaseExpiry)
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<PaymentOperation> claimReceivedOperations(
        @Param("leaseExpiry") LocalDateTime leaseExpiry,
        @Param("limit") int limit);
}
```

- [ ] **Step 3: Extract `PaymentWorkerConfig.java` for shared operation polling config**

```java
package com.yashmerino.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentWorkerConfig {

    @Value("${payment.worker.poll-size:5}")
    private int pollSize;

    @Value("${payment.worker.lease-seconds:30}")
    private int leaseSeconds;

    @Value("${payment.worker.worker-id:payment-worker-${random.value}}")
    private String workerId;

    @Value("${payment.worker.stripe-timeout-seconds:30}")
    private int stripeTimeoutSeconds;

    public int getPollSize() { return pollSize; }
    public int getLeaseSeconds() { return leaseSeconds; }
    public String getWorkerId() { return workerId; }
    public int getStripeTimeoutSeconds() { return stripeTimeoutSeconds; }
}
```

Add to `application.properties`:
```properties
payment.worker.poll-size=5
payment.worker.lease-seconds=30
payment.worker.stripe-timeout-seconds=30
```

- [ ] **Step 4: Create `PaymentWorker.java`**

```java
package com.yashmerino.ecommerce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.config.PaymentWorkerConfig;
import com.yashmerino.ecommerce.kafka.events.*;
import com.yashmerino.ecommerce.model.operations.PaymentOperation;
import com.yashmerino.ecommerce.repository.PaymentOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentWorker {

    private static final Logger log = LoggerFactory.getLogger(PaymentWorker.class);

    private final PaymentOperationRepository paymentOperationRepository;
    private final StripePaymentService stripePaymentService;
    private final OutboxPublisher outboxPublisher;
    private final PaymentWorkerConfig config;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public PaymentWorker(PaymentOperationRepository paymentOperationRepository,
                         StripePaymentService stripePaymentService,
                         OutboxPublisher outboxPublisher,
                         PaymentWorkerConfig config,
                         TransactionTemplate transactionTemplate,
                         ObjectMapper objectMapper) {
        this.paymentOperationRepository = paymentOperationRepository;
        this.stripePaymentService = stripePaymentService;
        this.outboxPublisher = outboxPublisher;
        this.config = config;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${payment.worker.poll-interval-ms:1000}")
    public void processPendingPayments() {
        LocalDateTime leaseExpiry = LocalDateTime.now().minusSeconds(config.getLeaseSeconds());
        List<PaymentOperation> operations = paymentOperationRepository.claimReceivedOperations(
            leaseExpiry, config.getPollSize());

        for (PaymentOperation op : operations) {
            processOperation(op);
        }
    }

    private void processOperation(PaymentOperation op) {
        try {
            op.assignStripeIdempotencyKey("payment-" + op.getMainPaymentId() + "-" + op.getRequestIdempotencyKey());

            transactionTemplate.executeWithoutResult(status -> {
                op.markProcessing(config.getWorkerId());
                paymentOperationRepository.save(op);
            });

            StripePaymentResult result = stripePaymentService.charge(
                op.getAmount(), op.getCurrency(), op.getPaymentMethodRef(),
                op.getStripeIdempotencyKey());

            boolean terminal = result.isTerminal();
            UUID eventId = UUID.randomUUID();
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            if (terminal) {
                transactionTemplate.executeWithoutResult(status -> {
                    if ("SUCCEEDED".equals(result.getStatus())) {
                        op.markSucceeded(result.getPaymentIntentId());
                    } else {
                        op.markFailed(result.getFailureCode(), result.getFailureMessage());
                    }
                    paymentOperationRepository.save(op);

                    PaymentResultEventV2 resultEvent = new PaymentResultEventV2(
                        eventId.toString(), "PaymentResultEventV2", 2, now, null,
                        op.getMainPaymentId(), "payment-service",
                        "payment-result:" + op.getMainPaymentId() + ":" + result.getStatus(),
                        op.getMainPaymentId(), op.getOrderId(),
                        op.getAmount().toString(), op.getCurrency(),
                        result.getPaymentIntentId(), result.getStatus(),
                        result.getFailureCode(), result.getFailureMessage()
                    );

                    outboxPublisher.enqueue(
                        eventId.toString(), "payment", op.getMainPaymentId(),
                        "PaymentResultEventV2", "payment.result.v2",
                        op.getMainPaymentId().toString(), resultEvent,
                        "payment-result:" + op.getMainPaymentId() + ":" + result.getStatus()
                    );
                });
            } else {
                transactionTemplate.executeWithoutResult(status -> {
                    op.markUnknown();
                    paymentOperationRepository.save(op);

                    PaymentReviewRequiredEventV2 reviewEvent = new PaymentReviewRequiredEventV2(
                        eventId.toString(), "PaymentReviewRequiredEventV2", 2, now, null,
                        op.getMainPaymentId(), "payment-service",
                        "payment-review:" + op.getMainPaymentId() + ":" + result.getFailureCode(),
                        op.getMainPaymentId(), op.getOrderId(),
                        result.getPaymentIntentId(),
                        result.getFailureCode() != null ? result.getFailureCode() : "UNKNOWN_OUTCOME",
                        null, now
                    );

                    outboxPublisher.enqueue(
                        eventId.toString(), "payment", op.getMainPaymentId(),
                        "PaymentReviewRequiredEventV2", "payment.review.required.v2",
                        op.getMainPaymentId().toString(), reviewEvent,
                        "payment-review:" + op.getMainPaymentId() + ":" + result.getFailureCode()
                    );
                });
            }
        } catch (Exception e) {
            log.error("Failed to process payment operation {}: {}", op.getId(), e.getMessage(), e);
        }
    }
}
```

- [ ] **Step 5: Update `StripePaymentService` interface and `StripePaymentServiceImpl` to accept idempotency key**

Interface:
```java
StripePaymentResult charge(BigDecimal amount, String currency, String paymentMethodRef, String idempotencyKey);
```

Implementation:
```java
@Override
public StripePaymentResult charge(BigDecimal amount, String currency, String paymentMethodRef, String idempotencyKey) {
    try {
        Map<String, Object> params = new HashMap<>();
        params.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValue());
        params.put("currency", currency.toLowerCase());
        params.put("payment_method", paymentMethodRef);
        params.put("confirm", true);
        params.put("off_session", true);

        Map<String, String> options = new HashMap<>();
        if (idempotencyKey != null) {
            options.put("idempotency_key", idempotencyKey);
        }

        PaymentIntent intent = PaymentIntent.create(params, RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build());

        String status = mapStripeStatus(intent.getStatus());
        boolean terminal = "succeeded".equals(intent.getStatus()) || "canceled".equals(intent.getStatus());

        if (terminal) {
            return new StripePaymentResult(intent.getId(), status, null, null);
        } else if ("requires_action".equals(intent.getStatus()) || "processing".equals(intent.getStatus())) {
            return new StripePaymentResult(intent.getId(), "UNKNOWN", "requires_action", null);
        } else {
            return new StripePaymentResult(intent.getId(), "FAILED",
                "stripe_" + intent.getStatus(), intent.getLastPaymentError() != null ?
                    intent.getLastPaymentError().getMessage() : null);
        }
    } catch (StripeException e) {
        return new StripePaymentResult(null, "FAILED", e.getCode(), e.getMessage());
    }
}

private String mapStripeStatus(String stripeStatus) {
    return switch (stripeStatus) {
        case "succeeded" -> "SUCCEEDED";
        case "canceled" -> "CANCELLED";
        case "requires_payment_method", "requires_confirmation" -> "FAILED";
        default -> "UNKNOWN";
    };
}
```

Also need to update `StripePaymentResult` to hold more fields:
- Add `failureCode`, `failureMessage`, `terminal` flag

```java
package com.yashmerino.ecommerce.model.stripe;

public class StripePaymentResult {

    private final String paymentIntentId;
    private final String status;
    private final String failureCode;
    private final String failureMessage;

    public StripePaymentResult(String paymentIntentId, String status,
                                String failureCode, String failureMessage) {
        this.paymentIntentId = paymentIntentId;
        this.status = status;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public String getPaymentIntentId() { return paymentIntentId; }
    public String getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }

    public boolean isTerminal() {
        return "SUCCEEDED".equals(status) || "FAILED".equals(status);
    }
}
```

- [ ] **Step 6: Add V2 `@KafkaListener` to `PaymentEventListener.java`**

```java
@KafkaListener(topics = "${payment.topics.payment-requested-v2}", groupId = "${spring.kafka.consumer.group-id}")
public void onPaymentRequestedV2(PaymentRequestedEventV2 event) {
    log.info("Received V2 payment request: paymentId={}, orderId={}", event.paymentId(), event.orderId());
    try {
        paymentService.processPaymentV2(event);
    } catch (Exception e) {
        log.error("Error processing V2 payment request: {}", e.getMessage(), e);
        throw e;
    }
}
```

Add the V2 process method in `PaymentService`:
```java
void processPaymentV2(PaymentRequestedEventV2 event);
```

In `PaymentServiceImpl`:
```java
@Override
@Transactional
public void processPaymentV2(PaymentRequestedEventV2 event) {
    if (inboxService.isAlreadyProcessed("payment-service", event.eventId())) {
        log.info("Already processed event: {}", event.eventId());
        return;
    }

    PaymentOperation operation = new PaymentOperation(
        event.paymentId(), event.orderId(),
        new BigDecimal(event.amount()), event.currency(),
        event.paymentMethodRef(), event.idempotencyKey()
    );
    paymentOperationRepository.save(operation);

    inboxService.markProcessed("payment-service", event.eventId(),
        "PaymentRequestedEventV2", event.correlationId(), event.paymentId());
}
```

- [ ] **Step 7: Add `InboxService` and `PaymentOperationRepository` injections to `PaymentServiceImpl`**

```java
private final InboxService inboxService;
private final PaymentOperationRepository paymentOperationRepository;
```

- [ ] **Step 8: Compile check**

```bash
cd ecommerce-platform-payment-service && mvn compile -q 2>&1 | tail -5
```

- [ ] **Step 9: Commit**

```bash
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/operations/
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/repository/PaymentOperationRepository.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/PaymentWorker.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/config/PaymentWorkerConfig.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/PaymentEventListener.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/PaymentService.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/impl/PaymentServiceImpl.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/StripePaymentService.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/impl/StripePaymentServiceImpl.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/stripe/StripePaymentResult.java
git add ecommerce-platform-payment-service/src/main/resources/application.properties
git commit -m "feat(payment): add V2 payment consumer + worker with Stripe outbox"
```

---

## Task A6: Payment Service Refund Operations

**Files:**
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/operations/RefundOperation.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/repository/RefundOperationRepository.java`
- Create: `ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/RefundWorker.java`

**Interfaces:**
- Consumes: Task A1 (V2 events), Task A3 (OutboxPublisher), Task A4 (InboxService), Task A2 (refund_operations table)
- Produces: RefundResultEventV2 via OutboxPublisher

- [ ] **Step 1: Create `RefundOperation.java` entity**

```java
package com.yashmerino.ecommerce.model.operations;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_operations")
public class RefundOperation {

    public enum Status {
        RECEIVED, PROCESSING, SUCCEEDED, FAILED, UNKNOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", nullable = false)
    private Long refundId;

    @Column(name = "main_payment_id", nullable = false)
    private Long mainPaymentId;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "external_payment_id", length = 255)
    private String externalPaymentId;

    @Column(name = "stripe_refund_id", length = 255)
    private String stripeRefundId;

    @Column(name = "request_idempotency_key", nullable = false, length = 255)
    private String requestIdempotencyKey;

    @Column(name = "stripe_idempotency_key", length = 255)
    private String stripeIdempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.RECEIVED;

    @Column(name = "failure_code", length = 100)
    private String failureCode;

    @Column(name = "failure_message", length = 500)
    private String failureMessage;

    @Column(name = "claimed_by", length = 255)
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public RefundOperation() {}

    public RefundOperation(Long refundId, Long mainPaymentId, Long orderId,
                           BigDecimal amount, String currency, String externalPaymentId,
                           String requestIdempotencyKey) {
        this.refundId = refundId;
        this.mainPaymentId = mainPaymentId;
        this.orderId = orderId;
        this.amount = amount;
        this.currency = currency;
        this.externalPaymentId = externalPaymentId;
        this.requestIdempotencyKey = requestIdempotencyKey;
        this.status = Status.RECEIVED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getRefundId() { return refundId; }
    public Long getMainPaymentId() { return mainPaymentId; }
    public Long getOrderId() { return orderId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getExternalPaymentId() { return externalPaymentId; }
    public String getStripeRefundId() { return stripeRefundId; }
    public String getRequestIdempotencyKey() { return requestIdempotencyKey; }
    public String getStripeIdempotencyKey() { return stripeIdempotencyKey; }
    public Status getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
    public String getClaimedBy() { return claimedBy; }

    public void assignStripeIdempotencyKey(String key) {
        this.stripeIdempotencyKey = key;
    }

    public void markProcessing(String claimer) {
        this.status = Status.PROCESSING;
        this.claimedBy = claimer;
        this.claimedAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void markSucceeded(String stripeRefundId) {
        this.status = Status.SUCCEEDED;
        this.stripeRefundId = stripeRefundId;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed(String failureCode, String failureMessage) {
        this.status = Status.FAILED;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
        this.updatedAt = LocalDateTime.now();
    }

    public void markUnknown() {
        this.status = Status.UNKNOWN;
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 2: Create `RefundOperationRepository.java`**

```java
package com.yashmerino.ecommerce.repository;

import com.yashmerino.ecommerce.model.operations.RefundOperation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface RefundOperationRepository extends JpaRepository<RefundOperation, Long> {

    Optional<RefundOperation> findByRefundId(Long refundId);

    @Query(value = """
        SELECT * FROM refund_operations
        WHERE status = 'RECEIVED'
          AND (claimed_at IS NULL OR claimed_at < :leaseExpiry)
        ORDER BY created_at ASC
        LIMIT :limit
        FOR UPDATE SKIP LOCKED
        """, nativeQuery = true)
    List<RefundOperation> claimReceivedOperations(
        @Param("leaseExpiry") LocalDateTime leaseExpiry,
        @Param("limit") int limit);
}
```

- [ ] **Step 3: Create `RefundWorker.java`**

```java
package com.yashmerino.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.config.PaymentWorkerConfig;
import com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2;
import com.yashmerino.ecommerce.kafka.events.RefundResultEventV2;
import com.yashmerino.ecommerce.model.operations.RefundOperation;
import com.yashmerino.ecommerce.repository.RefundOperationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

@Service
public class RefundWorker {

    private static final Logger log = LoggerFactory.getLogger(RefundWorker.class);

    private final RefundOperationRepository refundOperationRepository;
    private final StripePaymentService stripePaymentService;
    private final OutboxPublisher outboxPublisher;
    private final PaymentWorkerConfig config;
    private final TransactionTemplate transactionTemplate;
    private final ObjectMapper objectMapper;

    public RefundWorker(RefundOperationRepository refundOperationRepository,
                        StripePaymentService stripePaymentService,
                        OutboxPublisher outboxPublisher,
                        PaymentWorkerConfig config,
                        TransactionTemplate transactionTemplate,
                        ObjectMapper objectMapper) {
        this.refundOperationRepository = refundOperationRepository;
        this.stripePaymentService = stripePaymentService;
        this.outboxPublisher = outboxPublisher;
        this.config = config;
        this.transactionTemplate = transactionTemplate;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedDelayString = "${payment.worker.poll-interval-ms:1000}")
    public void processPendingRefunds() {
        LocalDateTime leaseExpiry = LocalDateTime.now().minusSeconds(config.getLeaseSeconds());
        List<RefundOperation> operations = refundOperationRepository.claimReceivedOperations(
            leaseExpiry, config.getPollSize());

        for (RefundOperation op : operations) {
            processRefund(op);
        }
    }

    private void processRefund(RefundOperation op) {
        try {
            op.assignStripeIdempotencyKey("refund-" + op.getRefundId() + "-" + op.getRequestIdempotencyKey());

            transactionTemplate.executeWithoutResult(status -> {
                op.markProcessing(config.getWorkerId());
                refundOperationRepository.save(op);
            });

            StripeRefundResult result = stripePaymentService.refund(
                op.getExternalPaymentId(), op.getAmount(), op.getStripeIdempotencyKey());

            UUID eventId = UUID.randomUUID();
            String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

            transactionTemplate.executeWithoutResult(status -> {
                if ("SUCCEEDED".equals(result.getStatus())) {
                    op.markSucceeded(result.getStripeRefundId());
                } else if ("FAILED".equals(result.getStatus())) {
                    op.markFailed(result.getFailureCode(), result.getFailureMessage());
                } else {
                    op.markUnknown();
                }
                refundOperationRepository.save(op);

                RefundResultEventV2 resultEvent = new RefundResultEventV2(
                    eventId.toString(), "RefundResultEventV2", 2, now, null,
                    op.getRefundId(), "payment-service",
                    "refund-result:" + op.getRefundId() + ":" + result.getStatus(),
                    op.getRefundId(), op.getOrderId(), op.getMainPaymentId(),
                    op.getExternalPaymentId(), result.getStripeRefundId(),
                    op.getAmount().toString(), op.getCurrency(),
                    result.getStatus(), result.getFailureCode(), result.getFailureMessage()
                );

                outboxPublisher.enqueue(
                    eventId.toString(), "refund", op.getRefundId(),
                    "RefundResultEventV2", "payment.refund.result.v2",
                    op.getMainPaymentId().toString(), resultEvent,
                    "refund-result:" + op.getRefundId() + ":" + result.getStatus()
                );
            });
        } catch (Exception e) {
            log.error("Failed to process refund operation {}: {}", op.getId(), e.getMessage(), e);
        }
    }

    @Transactional
    public void receiveRefundRequest(RefundRequestedEventV2 event) {
        if (inboxService.isAlreadyProcessed("payment-service", event.eventId())) {
            return;
        }

        RefundOperation operation = new RefundOperation(
            event.refundId(), event.paymentId(), event.orderId(),
            new BigDecimal(event.amount()), event.currency(),
            event.externalPaymentId(), event.requestIdempotencyKey()
        );
        refundOperationRepository.save(operation);

        inboxService.markProcessed("payment-service", event.eventId(),
            "RefundRequestedEventV2", event.correlationId(), event.refundId());
    }
}
```

- [ ] **Step 4: Add refund method to `StripePaymentService` interface**

```java
StripeRefundResult refund(String paymentIntentId, BigDecimal amount, String idempotencyKey);
```

Create `StripeRefundResult`:
```java
package com.yashmerino.ecommerce.model.stripe;

public class StripeRefundResult {

    private final String stripeRefundId;
    private final String status;
    private final String failureCode;
    private final String failureMessage;

    public StripeRefundResult(String stripeRefundId, String status,
                               String failureCode, String failureMessage) {
        this.stripeRefundId = stripeRefundId;
        this.status = status;
        this.failureCode = failureCode;
        this.failureMessage = failureMessage;
    }

    public String getStripeRefundId() { return stripeRefundId; }
    public String getStatus() { return status; }
    public String getFailureCode() { return failureCode; }
    public String getFailureMessage() { return failureMessage; }
}
```

Implementation in `StripePaymentServiceImpl`:
```java
@Override
public StripeRefundResult refund(String paymentIntentId, BigDecimal amount, String idempotencyKey) {
    try {
        Map<String, Object> params = new HashMap<>();
        params.put("payment_intent", paymentIntentId);
        params.put("amount", amount.multiply(BigDecimal.valueOf(100)).longValue());

        Refund stripeRefund = Refund.create(params, RequestOptions.builder()
            .setIdempotencyKey(idempotencyKey)
            .build());

        boolean succeeded = "succeeded".equals(stripeRefund.getStatus());
        return new StripeRefundResult(
            stripeRefund.getId(),
            succeeded ? "SUCCEEDED" : "FAILED",
            succeeded ? null : "stripe_" + stripeRefund.getStatus(),
            succeeded ? null : stripeRefund.getFailureMessage()
        );
    } catch (StripeException e) {
        return new StripeRefundResult(null, "FAILED", e.getCode(), e.getMessage());
    }
}
```

- [ ] **Step 5: Add V2 refund listener to `PaymentEventListener.java`**

```java
@KafkaListener(topics = "${payment.topics.refund-requested-v2}", groupId = "${spring.kafka.consumer.group-id}")
public void onRefundRequestedV2(RefundRequestedEventV2 event) {
    log.info("Received V2 refund request: refundId={}, paymentId={}", event.refundId(), event.paymentId());
    try {
        refundWorker.receiveRefundRequest(event);
    } catch (Exception e) {
        log.error("Error processing V2 refund request: {}", e.getMessage(), e);
        throw e;
    }
}
```

- [ ] **Step 6: Compile check**

```bash
cd ecommerce-platform-payment-service && mvn compile -q 2>&1 | tail -5
```

- [ ] **Step 7: Commit**

```bash
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/operations/RefundOperation.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/repository/RefundOperationRepository.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/RefundWorker.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/model/stripe/StripeRefundResult.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/StripePaymentService.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/service/impl/StripePaymentServiceImpl.java
git add ecommerce-platform-payment-service/src/main/java/com/yashmerino/ecommerce/kafka/PaymentEventListener.java
git commit -m "feat(payment): add refund operations and worker"
```

---

## Task A7: Payment Service V2 Tests

**Files:**
- Create: `ecommerce-platform-payment-service/src/test/java/com/yashmerino/ecommerce/service/OutboxPublisherTest.java`
- Create: `ecommerce-platform-payment-service/src/test/java/com/yashmerino/ecommerce/service/InboxServiceTest.java`
- Create: `ecommerce-platform-payment-service/src/test/java/com/yashmerino/ecommerce/service/PaymentWorkerTest.java`
- Create: `ecommerce-platform-payment-service/src/test/java/com/yashmerino/ecommerce/service/RefundWorkerTest.java`
- Create: `ecommerce-platform-payment-service/src/test/java/com/yashmerino/ecommerce/kafka/PaymentEventListenerV2Test.java`

**Interfaces:** Tests for Tasks A3, A4, A5, A6

- [ ] **Step 1: Write `OutboxPublisherTest.java`**

```java
package com.yashmerino.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.config.OutboxConfig;
import com.yashmerino.ecommerce.model.outbox.OutboxEvent;
import com.yashmerino.ecommerce.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository repository;
    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;
    @Mock
    private TransactionTemplate transactionTemplate;

    private ObjectMapper objectMapper;
    private OutboxConfig config;
    private OutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        config = new OutboxConfig();
        publisher = new OutboxPublisher(repository, kafkaTemplate, objectMapper, config, transactionTemplate);
    }

    @Test
    void enqueue_shouldSaveEvent() {
        publisher.enqueue("evt-1", "payment", 1L, "PaymentResultEventV2",
            "payment.result.v2", "1", "{\"status\":\"SUCCEEDED\"}", "key-1");
        verify(repository).save(any(OutboxEvent.class));
    }

    @Test
    void publishPending_shouldClaimAndPublish() {
        OutboxEvent event = new OutboxEvent("evt-1", "payment", 1L,
            "PaymentResultEventV2", "payment.result.v2", "1",
            "{\"status\":\"SUCCEEDED\"}", "key-1");
        when(repository.claimPendingEvents(any(), any(), anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenReturn(CompletableFuture.completedFuture(null));

        publisher.publishPending();

        verify(kafkaTemplate).send("payment.result.v2", "1", "{\"status\":\"SUCCEEDED\"}");
        verify(repository, times(2)).save(event);
    }

    @Test
    void publishPending_shouldRetryOnFailure() {
        OutboxEvent event = new OutboxEvent("evt-1", "payment", 1L,
            "PaymentResultEventV2", "payment.result.v2", "1",
            "{\"status\":\"SUCCEEDED\"}", "key-1");
        when(repository.claimPendingEvents(any(), any(), anyInt())).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
            .thenThrow(new RuntimeException("Kafka unavailable"));

        publisher.publishPending();

        verify(repository).save(event);
        assert event.getStatus().equals("RETRY") || event.getStatus().equals("DEAD_LETTER");
    }
}
```

- [ ] **Step 2: Write `InboxServiceTest.java`**

```java
package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private ProcessedEventRepository repository;

    @InjectMocks
    private InboxService inboxService;

    @Test
    void isAlreadyProcessed_shouldReturnTrueWhenExists() {
        when(repository.existsByConsumerNameAndEventId("svc", "evt-1")).thenReturn(true);
        assertTrue(inboxService.isAlreadyProcessed("svc", "evt-1"));
    }

    @Test
    void isAlreadyProcessed_shouldReturnFalseWhenNotExists() {
        when(repository.existsByConsumerNameAndEventId("svc", "evt-1")).thenReturn(false);
        assertFalse(inboxService.isAlreadyProcessed("svc", "evt-1"));
    }
}
```

- [ ] **Step 3: Write `PaymentWorkerTest.java`**

```java
package com.yashmerino.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.config.PaymentWorkerConfig;
import com.yashmerino.ecommerce.model.operations.PaymentOperation;
import com.yashmerino.ecommerce.model.stripe.StripePaymentResult;
import com.yashmerino.ecommerce.repository.PaymentOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentWorkerTest {

    @Mock
    private PaymentOperationRepository repository;
    @Mock
    private StripePaymentService stripePaymentService;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PaymentWorkerConfig config;
    private PaymentWorker worker;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        config = new PaymentWorkerConfig();
        objectMapper = new ObjectMapper();
        worker = new PaymentWorker(repository, stripePaymentService, outboxPublisher,
            config, transactionTemplate, objectMapper);

        when(transactionTemplate.executeWithoutResult(any())).thenAnswer(invocation -> {
            ((TransactionTemplate)invocation.getMock()).executeWithoutResult(
                (org.springframework.transaction.support.TransactionCallbackWithoutResult) status -> {});
            return null;
        });
    }

    @Test
    void processPendingPayments_shouldClaimAndProcess() {
        PaymentOperation op = new PaymentOperation(1L, 1L, BigDecimal.TEN, "USD", "pm_xxx", "key-1");
        when(repository.claimReceivedOperations(any(), anyInt())).thenReturn(List.of(op));
        when(stripePaymentService.charge(any(), any(), any(), any()))
            .thenReturn(new StripePaymentResult("pi_xxx", "SUCCEEDED", null, null));

        worker.processPendingPayments();

        verify(repository).save(op);
        verify(outboxPublisher).enqueue(anyString(), eq("payment"), eq(1L),
            eq("PaymentResultEventV2"), eq("payment.result.v2"), anyString(),
            any(), anyString());
    }

    @Test
    void processPendingPayments_shouldHandleFailure() {
        PaymentOperation op = new PaymentOperation(1L, 1L, BigDecimal.TEN, "USD", "pm_xxx", "key-1");
        when(repository.claimReceivedOperations(any(), anyInt())).thenReturn(List.of(op));
        when(stripePaymentService.charge(any(), any(), any(), any()))
            .thenReturn(new StripePaymentResult("pi_xxx", "FAILED", "card_declined", null));

        worker.processPendingPayments();

        verify(outboxPublisher).enqueue(anyString(), eq("payment"), eq(1L),
            eq("PaymentResultEventV2"), eq("payment.result.v2"), anyString(),
            any(), anyString());
    }
}
```

- [ ] **Step 4: Write `RefundWorkerTest.java`**

```java
package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.config.PaymentWorkerConfig;
import com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2;
import com.yashmerino.ecommerce.model.operations.RefundOperation;
import com.yashmerino.ecommerce.model.stripe.StripeRefundResult;
import com.yashmerino.ecommerce.repository.RefundOperationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundWorkerTest {

    @Mock
    private RefundOperationRepository repository;
    @Mock
    private StripePaymentService stripePaymentService;
    @Mock
    private OutboxPublisher outboxPublisher;
    @Mock
    private InboxService inboxService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private PaymentWorkerConfig config;
    private RefundWorker worker;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        config = new PaymentWorkerConfig();
        objectMapper = new ObjectMapper();
        worker = new RefundWorker(repository, stripePaymentService, outboxPublisher,
            config, transactionTemplate, objectMapper);
    }

    @Test
    void receiveRefundRequest_shouldCreateOperation() {
        RefundRequestedEventV2 event = new RefundRequestedEventV2(
            "evt-1", "RefundRequestedEventV2", 2, "2026-01-01", "corr-1",
            1L, "main", "key-1", 1L, 1L, 1L, "pi_xxx",
            "10.00", "USD", "test refund", "admin", "req-key-1");
        when(inboxService.isAlreadyProcessed("payment-service", "evt-1")).thenReturn(false);

        worker.receiveRefundRequest(event);

        verify(repository).save(any(RefundOperation.class));
    }

    @Test
    void receiveRefundRequest_shouldSkipDuplicates() {
        RefundRequestedEventV2 event = new RefundRequestedEventV2(
            "evt-1", "RefundRequestedEventV2", 2, "2026-01-01", "corr-1",
            1L, "main", "key-1", 1L, 1L, 1L, "pi_xxx",
            "10.00", "USD", "test refund", "admin", "req-key-1");
        when(inboxService.isAlreadyProcessed("payment-service", "evt-1")).thenReturn(true);

        worker.receiveRefundRequest(event);

        verify(repository, never()).save(any());
    }

    @Test
    void processPendingRefunds_shouldClaimAndProcess() {
        RefundOperation op = new RefundOperation(1L, 1L, 1L, BigDecimal.TEN, "USD", "pi_xxx", "key-1");
        when(repository.claimReceivedOperations(any(), anyInt())).thenReturn(List.of(op));
        when(stripePaymentService.refund(anyString(), any(), anyString()))
            .thenReturn(new StripeRefundResult("re_xxx", "SUCCEEDED", null, null));

        worker.processPendingRefunds();

        verify(outboxPublisher).enqueue(anyString(), eq("refund"), eq(1L),
            eq("RefundResultEventV2"), eq("payment.refund.result.v2"), anyString(),
            any(), anyString());
    }
}
```

- [ ] **Step 5: Write `PaymentEventListenerV2Test.java`**

```java
package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEventV2;
import com.yashmerino.ecommerce.service.PaymentService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerV2Test {

    @Mock
    private PaymentService paymentService;

    @InjectMocks
    private PaymentEventListener listener;

    @Test
    void onPaymentRequestedV2_shouldDelegate() {
        PaymentRequestedEventV2 event = new PaymentRequestedEventV2(
            "evt-1", "PaymentRequestedEventV2", 2, "2026-01-01", "corr-1",
            1L, "main", "key-1", 1L, 1L, "10.00", "USD", "pm_xxx");

        listener.onPaymentRequestedV2(event);

        verify(paymentService).processPaymentV2(event);
    }
}
```

- [ ] **Step 6: Run tests**

```bash
cd ecommerce-platform-payment-service && mvn test -q 2>&1 | tail -10
```
Expected: All tests pass.

- [ ] **Step 7: Commit**

```bash
git add ecommerce-platform-payment-service/src/test/java/com/yashmerino/ecommerce/
git commit -m "test(payment): add V2 outbox/inbox/worker tests"
```

---
---

# Work Stream B: Main Server V2

## Task B1: Main Server V5 Migration + V2 Event Classes

**Files:**
- Create: `ecommerce-platform-server/src/main/resources/db/migration/V5__add_v2_tables.sql`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/kafka/events/PaymentResultEventV2.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/kafka/events/PaymentReviewRequiredEventV2.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/kafka/events/RefundRequestedEventV2.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/kafka/events/RefundResultEventV2.java`

**Interfaces:**
- Produces: V2 event classes, processed_events table, refunds table

- [ ] **Step 1: Create `V5__add_v2_tables.sql`**

```sql
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
```

- [ ] **Step 2: Create V2 event classes for Main Server**

Same records as Task A1 but in package `com.yashmerino.ecommerce.kafka.events` under the server project.

Create: `PaymentResultEventV2.java`, `PaymentReviewRequiredEventV2.java`, `RefundRequestedEventV2.java`, `RefundResultEventV2.java`.

- [ ] **Step 3: Add V2 topic properties to server `application.properties`**

```properties
payment.topics.payment-result-v2=payment.result.v2
payment.topics.payment-review-v2=payment.review.required.v2
payment.topics.refund-requested-v2=payment.refund.requested.v2
payment.topics.refund-result-v2=payment.refund.result.v2
payment.topics.notification-requested-v2=notification.requested.v2
```

- [ ] **Step 4: Compile check**

```bash
cd ecommerce-platform-server && mvn compile -q 2>&1 | tail -5
```

- [ ] **Step 5: Commit**

```bash
git add ecommerce-platform-server/src/main/resources/db/migration/V5__add_v2_tables.sql
git add ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/kafka/events/
git add ecommerce-platform-server/src/main/resources/application.properties
git commit -m "feat(server): add V2 tables and event classes"
```

---

## Task B2: Main Server Inbox/Processed Events Infrastructure

**Files:**
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/model/processed/ProcessedEvent.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/repository/ProcessedEventRepository.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/service/InboxService.java`

**Interfaces:**
- Consumes: Task B1 (processed_events table)
- Produces: `InboxService.isProcessed(consumerName, eventId)`, `InboxService.markProcessed(consumerName, eventId, eventType, correlationId, aggregateId)`

- [ ] **Step 1: Create `ProcessedEvent.java`** (same as Task A4 Step 1 but in server package)

- [ ] **Step 2: Create `ProcessedEventRepository.java`** and `InboxService.java`** (same as Task A4 Steps 2-3)

- [ ] **Step 3: Compile check + commit**

---

## Task B3: Main Server V2 Payment Result Consumer

**Files:**
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/kafka/PaymentResultV2Consumer.java`
- Modify: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/services/CheckoutService.java` (add payment result V2 method)

**Interfaces:**
- Consumes: Task B1 (V2 event classes), Task B2 (InboxService)
- Produces: Status update on Payment and Order entities (version-guarded)

- [ ] **Step 1: Create `PaymentResultV2Consumer.java`**

```java
package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentResultEventV2;
import com.yashmerino.ecommerce.services.CheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultV2Consumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentResultV2Consumer.class);

    private final CheckoutService checkoutService;

    public PaymentResultV2Consumer(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @KafkaListener(topics = "${payment.topics.payment-result-v2}",
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentResult(PaymentResultEventV2 event) {
        log.info("Received V2 payment result: paymentId={}, status={}",
            event.paymentId(), event.status());
        try {
            checkoutService.processPaymentResultV2(event);
        } catch (Exception e) {
            log.error("Error processing V2 payment result: {}", e.getMessage(), e);
            throw e;
        }
    }
}
```

- [ ] **Step 2: Add `processPaymentResultV2` to `CheckoutService`**

```java
@Transactional
public void processPaymentResultV2(PaymentResultEventV2 event) {
    if (inboxService.isAlreadyProcessed("main-server", event.eventId())) {
        log.info("Already processed payment result event: {}", event.eventId());
        return;
    }

    Payment payment = paymentRepository.findByPaymentId(event.paymentId())
        .orElseThrow(() -> new RuntimeException("Payment not found: " + event.paymentId()));

    Order order = payment.getOrder();

    if ("SUCCEEDED".equals(event.status())) {
        int updated = paymentRepository.updateStatusAndVersion(
            event.paymentId(), PaymentStatus.PENDING, PaymentStatus.SUCCEEDED, payment.getVersion());
        if (updated == 0) throw new OptimisticLockException("Payment version conflict");
        
        int orderUpdated = orderRepository.updateOrderStatusAndVersion(
            order.getId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAID, order.getVersion());
        if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");
    } else if ("FAILED".equals(event.status())) {
        int updated = paymentRepository.updateStatusAndVersion(
            event.paymentId(), PaymentStatus.PENDING, PaymentStatus.FAILED, payment.getVersion());
        if (updated == 0) throw new OptimisticLockException("Payment version conflict");
        
        int orderUpdated = orderRepository.updateOrderStatusAndVersion(
            order.getId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_FAILED, order.getVersion());
        if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");
    }

    inboxService.markProcessed("main-server", event.eventId(), "PaymentResultEventV2",
        null, event.paymentId());
}
```

Add `inboxService` field + constructor injection to `CheckoutService`.

- [ ] **Step 3: Add `PaymentReviewRequiredV2Consumer.java`**

```java
package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentReviewRequiredEventV2;
import com.yashmerino.ecommerce.services.CheckoutService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentReviewRequiredV2Consumer {

    private static final Logger log = LoggerFactory.getLogger(PaymentReviewRequiredV2Consumer.class);

    private final CheckoutService checkoutService;

    public PaymentReviewRequiredV2Consumer(CheckoutService checkoutService) {
        this.checkoutService = checkoutService;
    }

    @KafkaListener(topics = "${payment.topics.payment-review-v2}",
                   groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentReviewRequired(PaymentReviewRequiredEventV2 event) {
        log.info("Received payment review required: paymentId={}, reason={}",
            event.paymentId(), event.reasonCode());
        try {
            checkoutService.handlePaymentReview(event);
        } catch (Exception e) {
            log.error("Error handling payment review: {}", e.getMessage(), e);
            throw e;
        }
    }
}
```

Add `handlePaymentReview` to `CheckoutService`:
```java
@Transactional
public void handlePaymentReview(PaymentReviewRequiredEventV2 event) {
    if (inboxService.isAlreadyProcessed("main-server", event.eventId())) {
        return;
    }

    Payment payment = paymentRepository.findByPaymentId(event.paymentId())
        .orElseThrow(() -> new RuntimeException("Payment not found: " + event.paymentId()));

    int updated = paymentRepository.updateStatusAndVersion(
        event.paymentId(), PaymentStatus.PENDING, PaymentStatus.REVIEW, payment.getVersion());
    if (updated == 0) throw new OptimisticLockException("Payment version conflict");

    Order order = payment.getOrder();
    int orderUpdated = orderRepository.updateOrderStatusAndVersion(
        order.getId(), OrderStatus.PAYMENT_PENDING, OrderStatus.PAYMENT_REVIEW, order.getVersion());
    if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");

    inboxService.markProcessed("main-server", event.eventId(),
        "PaymentReviewRequiredEventV2", null, event.paymentId());
}
```

- [ ] **Step 4: Compile check + commit**

---

## Task B4: Main Server Refund Controller + Service

**Files:**
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/controllers/RefundController.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/services/RefundService.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/model/dto/RefundRequestDTO.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/model/domain/Refund.java`
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/repository/RefundRepository.java`

**Interfaces:**
- Consumes: Task B1 (refunds table, RefundRequestedEventV2), Task B2 (InboxService)
- Produces: REST endpoint POST /api/orders/{orderId}/refund, RefundRequestedEventV2 via outbox

- [ ] **Step 1: Create `RefundRequestDTO.java`**

```java
package com.yashmerino.ecommerce.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RefundRequestDTO(
    @NotBlank @Size(max = 500) String reason
) {}
```

- [ ] **Step 2: Create `Refund.java` entity**

```java
package com.yashmerino.ecommerce.model.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refunds")
public class Refund {

    public enum Status { PENDING, SUCCEEDED, FAILED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "order_id", nullable = false)
    private Long orderId;

    @Column(name = "payment_id", nullable = false)
    private Long paymentId;

    @Column(nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 500)
    private String reason;

    @Column(name = "requested_by", nullable = false)
    private Long requestedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private Status status = Status.PENDING;

    @Column(name = "request_idempotency_key", nullable = false, length = 255)
    private String requestIdempotencyKey;

    @Column(name = "external_refund_id", length = 255)
    private String externalRefundId;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Refund() {}

    public Refund(Long orderId, Long paymentId, BigDecimal amount, String currency,
                  String reason, Long requestedBy, String requestIdempotencyKey) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.requestIdempotencyKey = requestIdempotencyKey;
        this.status = Status.PENDING;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() { return id; }
    public Long getOrderId() { return orderId; }
    public Long getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public String getCurrency() { return currency; }
    public String getReason() { return reason; }
    public Long getRequestedBy() { return requestedBy; }
    public Status getStatus() { return status; }
    public String getRequestIdempotencyKey() { return requestIdempotencyKey; }
    public String getExternalRefundId() { return externalRefundId; }
    public Long getVersion() { return version; }

    public void markSucceeded(String externalRefundId) {
        this.status = Status.SUCCEEDED;
        this.externalRefundId = externalRefundId;
        this.updatedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = Status.FAILED;
        this.updatedAt = LocalDateTime.now();
    }
}
```

- [ ] **Step 3: Create `RefundRepository.java`**

```java
package com.yashmerino.ecommerce.repository;

import com.yashmerino.ecommerce.model.domain.Refund;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface RefundRepository extends JpaRepository<Refund, Long> {

    boolean existsByRequestIdempotencyKey(String requestIdempotencyKey);
}
```

- [ ] **Step 4: Create `RefundService.java`**

```java
package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2;
import com.yashmerino.ecommerce.model.domain.*;
import com.yashmerino.ecommerce.model.dto.RefundRequestDTO;
import com.yashmerino.ecommerce.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
public class RefundService {

    private static final Logger log = LoggerFactory.getLogger(RefundService.class);

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    public RefundService(RefundRepository refundRepository, OrderRepository orderRepository,
                         PaymentRepository paymentRepository, OutboxService outboxService) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public Refund requestRefund(Long orderId, Long userId, RefundRequestDTO dto) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new RuntimeException("Order not found: " + orderId));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new RuntimeException("Order must be in PAID status to request refund");
        }

        if (!order.getUserId().equals(userId)) {
            throw new RuntimeException("Order does not belong to user");
        }

        Payment payment = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED)
            .orElseThrow(() -> new RuntimeException("No successful payment found for order"));

        String idempotencyKey = "refund-request:" + orderId + ":" + dto.reason().hashCode();

        if (refundRepository.existsByRequestIdempotencyKey(idempotencyKey)) {
            throw new RuntimeException("Duplicate refund request");
        }

        Refund refund = new Refund(orderId, payment.getId(), order.getTotalAmount(),
            order.getCurrency(), dto.reason(), userId, idempotencyKey);
        refund = refundRepository.save(refund);

        int updated = orderRepository.updateOrderStatusAndVersion(
            orderId, OrderStatus.PAID, OrderStatus.REFUND_PENDING, order.getVersion());
        if (updated == 0) throw new OptimisticLockException("Order version conflict");

        int paymentUpdated = paymentRepository.updateStatusAndVersion(
            payment.getId(), PaymentStatus.SUCCEEDED, PaymentStatus.REFUND_PENDING, payment.getVersion());
        if (paymentUpdated == 0) throw new OptimisticLockException("Payment version conflict");

        String eventId = UUID.randomUUID().toString();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        RefundRequestedEventV2 event = new RefundRequestedEventV2(
            eventId, "RefundRequestedEventV2", 2, now, null,
            refund.getId(), "main-server", idempotencyKey,
            refund.getId(), orderId, payment.getId(),
            payment.getExternalPaymentId(),
            refund.getAmount().toString(), refund.getCurrency(),
            dto.reason(), userId.toString(), idempotencyKey
        );

        outboxService.saveOutboxEvent(eventId, "refund", refund.getId(),
            "RefundRequestedEventV2", "payment.refund.requested.v2",
            payment.getId().toString(), event, idempotencyKey);

        return refund;
    }
}
```

- [ ] **Step 5: Create `RefundController.java`**

```java
package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.domain.Refund;
import com.yashmerino.ecommerce.model.dto.RefundRequestDTO;
import com.yashmerino.ecommerce.services.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/orders")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping("/{orderId}/refund")
    public ResponseEntity<?> requestRefund(
            @PathVariable Long orderId,
            @Valid @RequestBody RefundRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        Long userId = Long.parseLong(userDetails.getUsername());
        Refund refund = refundService.requestRefund(orderId, userId, dto);
        return ResponseEntity.ok(refund);
    }
}
```

- [ ] **Step 6: Compile check + commit**

---

## Task B5: Main Server Refund Result Consumer

**Files:**
- Create: `ecommerce-platform-server/src/main/java/com/yashmerino/ecommerce/kafka/RefundResultV2Consumer.java`

**Interfaces:**
- Consumes: Task B1 (RefundResultEventV2), Task B2 (InboxService)
- Produces: Version-guarded status update (REFUND_PENDING → REFUNDED/REFUND_FAILED)

- [ ] **Step 1: Create `RefundResultV2Consumer.java`**

```java
package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.RefundResultEventV2;
import com.yashmerino.ecommerce.model.domain.*;
import com.yashmerino.ecommerce.repository.*;
import com.yashmerino.ecommerce.services.InboxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class RefundResultV2Consumer {

    private static final Logger log = LoggerFactory.getLogger(RefundResultV2Consumer.class);

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final InboxService inboxService;

    public RefundResultV2Consumer(RefundRepository refundRepository, OrderRepository orderRepository,
                                  PaymentRepository paymentRepository, InboxService inboxService) {
        this.refundRepository = refundRepository;
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.inboxService = inboxService;
    }

    @KafkaListener(topics = "${payment.topics.refund-result-v2}",
                   groupId = "${spring.kafka.consumer.group-id}")
    @Transactional
    public void onRefundResult(RefundResultEventV2 event) {
        log.info("Received V2 refund result: refundId={}, status={}",
            event.refundId(), event.status());

        if (inboxService.isAlreadyProcessed("main-server", event.eventId())) {
            return;
        }

        Refund refund = refundRepository.findById(event.refundId())
            .orElseThrow(() -> new RuntimeException("Refund not found: " + event.refundId()));

        if ("SUCCEEDED".equals(event.status())) {
            refund.markSucceeded(event.stripeRefundId());
            refundRepository.save(refund);

            int orderUpdated = orderRepository.updateOrderStatusAndVersion(
                refund.getOrderId(), OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED, refund.getVersion());
            if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");

            Payment payment = paymentRepository.findById(refund.getPaymentId())
                .orElseThrow(() -> new RuntimeException("Payment not found: " + refund.getPaymentId()));
            int paymentUpdated = paymentRepository.updateStatusAndVersion(
                refund.getPaymentId(), PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED, payment.getVersion());
            if (paymentUpdated == 0) throw new OptimisticLockException("Payment version conflict");

        } else if ("FAILED".equals(event.status())) {
            refund.markFailed();
            refundRepository.save(refund);

            int orderUpdated = orderRepository.updateOrderStatusAndVersion(
                refund.getOrderId(), OrderStatus.REFUND_PENDING, OrderStatus.REFUND_FAILED, refund.getVersion());
            if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");
        }

        inboxService.markProcessed("main-server", event.eventId(),
            "RefundResultEventV2", null, event.refundId());
    }
}
```

- [ ] **Step 2: Add `InboxService` injection for `CheckoutService` — add the `inboxService` field if not already present**

- [ ] **Step 3: Compile check + commit**

---

# Work Stream C: Notification Service V2

## Task C1: Notification Service V2 Migration + V2 Event Class

**Files:**
- Create: `ecommerce-platform-notification-service/src/main/resources/db/migration/V2__add_v2_tables.sql`
- Create: `ecommerce-platform-notification-service/src/main/java/com/yashmerino/ecommerce/kafka/events/NotificationRequestedEventV2.java`

**Interfaces:**
- Produces: processed_events table, V2 event class

- [ ] **Step 1: Create `V2__add_v2_tables.sql`**

```sql
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
```

- [ ] **Step 2: Create `NotificationRequestedEventV2.java`**

```java
package com.yashmerino.ecommerce.kafka.events;

import java.util.Map;

public record NotificationRequestedEventV2(
    String eventId,
    String eventType,
    int eventVersion,
    String occurredAt,
    String correlationId,
    Long aggregateId,
    String producer,
    String idempotencyKey,
    String notificationType,
    String contactType,
    String contact,
    Map<String, Object> payload
) {
    public NotificationRequestedEventV2 {
        eventVersion = 2;
    }
}
```

- [ ] **Step 3: Add V2 topic to notification `application.properties`**

```properties
notification.topics.notification-requested-v2=notification.requested.v2
```

- [ ] **Step 4: Compile check + commit**

---

## Task C2: Notification Service Inbox + V2 Consumer

**Files:**
- Create: `ecommerce-platform-notification-service/src/main/java/com/yashmerino/ecommerce/model/processed/ProcessedEvent.java`
- Create: `ecommerce-platform-notification-service/src/main/java/com/yashmerino/ecommerce/repository/ProcessedEventRepository.java`
- Create: `ecommerce-platform-notification-service/src/main/java/com/yashmerino/ecommerce/service/InboxService.java`
- Modify: `ecommerce-platform-notification-service/src/main/java/com/yashmerino/ecommerce/kafka/NotificationEventListener.java` (add V2 listener)

**Interfaces:**
- Consumes: Task C1 (processed_events table, NotificationRequestedEventV2)
- Produces: Idempotent notification processing

- [ ] **Step 1: Create `ProcessedEvent.java`, `ProcessedEventRepository.java`, `InboxService.java`** (same pattern as Task A4)

- [ ] **Step 2: Add V2 listener to `NotificationEventListener.java`**

```java
@KafkaListener(topics = "${notification.topics.notification-requested-v2}",
               groupId = "${spring.kafka.consumer.group-id}")
public void onNotificationRequestedV2(NotificationRequestedEventV2 event) {
    log.info("Received V2 notification request: type={}, contact={}",
        event.notificationType(), event.contact());
    try {
        notificationService.sendNotificationV2(event);
    } catch (Exception e) {
        log.error("Error processing V2 notification: {}", e.getMessage(), e);
        throw e;
    }
}
```

- [ ] **Step 3: Add `sendNotificationV2` to `NotificationService`**

```java
void sendNotificationV2(NotificationRequestedEventV2 event);
```

Implementation:
```java
@Override
@Transactional
public void sendNotificationV2(NotificationRequestedEventV2 event) {
    if (inboxService.isAlreadyProcessed("notification-service", event.eventId())) {
        log.info("Already processed notification event: {}", event.eventId());
        return;
    }

    Notification notification = new Notification();
    notification.setContact(event.contact());
    notification.setContactType(event.contactType());
    notification.setNotificationType(event.notificationType());
    notification.setPayload(convertPayloadToString(event.payload()));
    notification.setStatus(NotificationStatus.PENDING);
    notification.setEventId(event.eventId());
    notification.setBusinessIdempotencyKey(event.idempotencyKey());
    notification = notificationRepository.save(notification);

    try {
        NotificationContent content = buildContent(event.notificationType(), event.payload());
        NotificationSender sender = senderFactory.getSender(event.contactType());
        sender.send(event.contact(), content);

        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.now());
        notificationRepository.save(notification);
    } catch (Exception e) {
        log.error("Failed to send notification: {}", e.getMessage(), e);
        notification.setStatus(NotificationStatus.FAILED);
        notification.setLastError(e.getMessage());
        notificationRepository.save(notification);
    }

    inboxService.markProcessed("notification-service", event.eventId(),
        "NotificationRequestedEventV2", event.correlationId(), event.aggregateId());
}

private String convertPayloadToString(Map<String, Object> payload) {
    try {
        return objectMapper.writeValueAsString(payload);
    } catch (JsonProcessingException e) {
        return "{}";
    }
}
```

- [ ] **Step 4: Add `eventId` and `businessIdempotencyKey` fields to `Notification.java` entity**

Add fields:
```java
@Column(name = "event_id", length = 36)
private String eventId;

@Column(name = "business_idempotency_key", length = 255)
private String businessIdempotencyKey;

@Column(name = "claimed_by", length = 255)
private String claimedBy;

@Column(name = "claimed_at")
private LocalDateTime claimedAt;
```

Add getters/setters for these.

- [ ] **Step 5: Compile check + commit**
