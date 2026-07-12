package com.yashmerino.ecommerce.kafka.events;

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
