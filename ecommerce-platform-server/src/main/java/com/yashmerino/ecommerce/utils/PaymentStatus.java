package com.yashmerino.ecommerce.utils;

/**
 * Payment status enum.
 */
public enum PaymentStatus {
    AWAITING_PAYMENT_METHOD,
    PENDING,
    SUCCEEDED,
    FAILED,
    REVIEW,
    CANCELLED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED,
    EXPIRED
}
