package com.yashmerino.ecommerce.model.outbox;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
public class OutboxEvent {

    public enum Status {
        PENDING, PROCESSING, RETRY, PUBLISHED, DEAD_LETTER
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true, length = 36)
    private String eventId;

    @Column(name = "aggregate_type")
    private String aggregateType;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "event_version")
    private int eventVersion = 2;

    @Column(name = "topic")
    private String topic;

    @Column(name = "event_key")
    private String eventKey;

    @Column(name = "payload", columnDefinition = "TEXT")
    private String payload;

    @Column(name = "idempotency_key")
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "retry_count")
    private int retryCount;

    @Column(name = "max_retries")
    private int maxRetries = 5;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "published_at")
    private LocalDateTime publishedAt;

    @Column(name = "last_error")
    private String lastError;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public void markProcessing(String claimer) {
        this.status = Status.PROCESSING;
        this.claimedBy = claimer;
        this.claimedAt = LocalDateTime.now();
    }

    public void markPublished() {
        this.status = Status.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
        this.claimedBy = null;
        this.claimedAt = null;
    }

    public void markRetry(String error) {
        this.retryCount++;
        this.lastError = error;
        if (this.retryCount >= this.maxRetries) {
            this.status = Status.DEAD_LETTER;
        } else {
            this.status = Status.RETRY;
            this.nextRetryAt = LocalDateTime.now().plusSeconds((long) Math.pow(2, this.retryCount));
        }
        this.claimedBy = null;
        this.claimedAt = null;
    }
}
