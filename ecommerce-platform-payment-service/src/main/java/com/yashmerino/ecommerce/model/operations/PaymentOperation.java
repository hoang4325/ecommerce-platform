package com.yashmerino.ecommerce.model.operations;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "payment_operations")
@Getter
@Setter
@NoArgsConstructor
public class PaymentOperation {

    public enum Status {
        RECEIVED, PROCESSING, SUCCEEDED, FAILED, UNKNOWN
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "main_payment_id", unique = true)
    private Long mainPaymentId;

    @Column(name = "order_id")
    private Long orderId;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private java.math.BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency;

    @Column(name = "payment_method_ref")
    private String paymentMethodRef;

    @Column(name = "request_idempotency_key", unique = true)
    private String requestIdempotencyKey;

    @Column(name = "stripe_idempotency_key", unique = true)
    private String stripeIdempotencyKey;

    @Column(name = "stripe_payment_intent_id", unique = true)
    private String stripePaymentIntentId;

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

    public void markSucceeded(String stripePaymentIntentId) {
        this.status = Status.SUCCEEDED;
        this.stripePaymentIntentId = stripePaymentIntentId;
        this.claimedBy = null;
        this.claimedAt = null;
        this.paymentMethodRef = null;
    }

    public void markFailed(String code, String message) {
        this.status = Status.FAILED;
        this.failureCode = code;
        this.failureMessage = message;
        this.claimedBy = null;
        this.claimedAt = null;
        this.paymentMethodRef = null;
    }

    public void markUnknown() {
        this.status = Status.UNKNOWN;
        this.claimedBy = null;
        this.claimedAt = LocalDateTime.now();
    }
}
