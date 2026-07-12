package com.yashmerino.ecommerce.utils;

/**
 * Order status enum.
 */
public enum OrderStatus {
    CREATED,
    PAYMENT_PENDING,
    PAID,
    PAYMENT_FAILED,
    PAYMENT_REVIEW,
    CANCELLED,
    EXPIRED,
    REFUND_PENDING,
    REFUNDED,
    REFUND_FAILED
}
