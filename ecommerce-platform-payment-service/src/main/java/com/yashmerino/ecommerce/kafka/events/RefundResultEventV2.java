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
