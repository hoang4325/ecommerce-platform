package com.yashmerino.ecommerce.model.operations;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_operations")
@Getter
@Setter
@NoArgsConstructor
public class RefundOperation {

    public enum Status {
        RECEIVED, PROCESSING, SUCCEEDED, FAILED, UNKNOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "refund_id", unique = true)
    private Long refundId;

    @Column(name = "main_payment_id")
    private Long mainPaymentId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "external_payment_id")
    private String externalPaymentId;

    @Column(name = "stripe_refund_id", unique = true)
    private String stripeRefundId;

    @Column(name = "request_idempotency_key", unique = true)
    private String requestIdempotencyKey;

    @Column(name = "stripe_idempotency_key", unique = true)
    private String stripeIdempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private Status status = Status.RECEIVED;

    @Column(name = "failure_code")
    private String failureCode;

    @Column(name = "failure_message")
    private String failureMessage;

    @Column(name = "claimed_by")
    private String claimedBy;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
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

    public void assignStripeIdempotencyKey(String key) {
        this.stripeIdempotencyKey = key;
    }

    public void markProcessing(String claimer) {
        this.status = Status.PROCESSING;
        this.claimedBy = claimer;
        this.claimedAt = LocalDateTime.now();
    }

    public void markSucceeded(String stripeRefundId) {
        this.status = Status.SUCCEEDED;
        this.stripeRefundId = stripeRefundId;
        this.claimedBy = null;
        this.claimedAt = null;
    }

    public void markFailed(String code, String message) {
        this.status = Status.FAILED;
        this.failureCode = code;
        this.failureMessage = message;
        this.claimedBy = null;
        this.claimedAt = null;
    }

    public void markUnknown() {
        this.status = Status.UNKNOWN;
        this.claimedBy = null;
        this.claimedAt = LocalDateTime.now();
    }
}
