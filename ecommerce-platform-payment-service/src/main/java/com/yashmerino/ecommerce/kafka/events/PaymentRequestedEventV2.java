package com.yashmerino.ecommerce.kafka.events;

import com.fasterxml.jackson.annotation.JsonAlias;

public record PaymentRequestedEventV2(
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
        @JsonAlias("paymentMethodRef") String paymentMethodId
) {
    public PaymentRequestedEventV2 {
        eventVersion = 2;
    }
}
