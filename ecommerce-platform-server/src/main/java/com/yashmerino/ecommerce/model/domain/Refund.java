package com.yashmerino.ecommerce.model.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "refunds")
public class Refund {

    public enum Status {
        PENDING, SUCCEEDED, FAILED
    }

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
    @Column(nullable = false)
    private Status status = Status.PENDING;

    @Column(name = "request_idempotency_key", nullable = false)
    private String requestIdempotencyKey;

    @Column(name = "external_refund_id")
    private String externalRefundId;

    @Version
    private Long version;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Refund(Long orderId, Long paymentId, BigDecimal amount, String currency, String reason, Long requestedBy, String requestIdempotencyKey) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.currency = currency;
        this.reason = reason;
        this.requestedBy = requestedBy;
        this.requestIdempotencyKey = requestIdempotencyKey;
        this.status = Status.PENDING;
    }

    public void markSucceeded(String externalRefundId) {
        this.status = Status.SUCCEEDED;
        this.externalRefundId = externalRefundId;
    }

    public void markFailed() {
        this.status = Status.FAILED;
    }
}
