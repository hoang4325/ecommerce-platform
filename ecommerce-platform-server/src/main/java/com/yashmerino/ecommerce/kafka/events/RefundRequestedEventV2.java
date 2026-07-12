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
