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
