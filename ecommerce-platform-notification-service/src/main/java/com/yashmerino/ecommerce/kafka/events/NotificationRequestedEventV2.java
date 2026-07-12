package com.yashmerino.ecommerce.kafka.events;

import java.util.Map;

public record NotificationRequestedEventV2(
    String eventId,
    String eventType,
    int eventVersion,
    String occurredAt,
    String correlationId,
    Long aggregateId,
    String producer,
    String idempotencyKey,
    String notificationType,
    String contactType,
    String contact,
    Map<String, Object> payload
) {
    public NotificationRequestedEventV2 {
        eventVersion = 2;
    }
}
