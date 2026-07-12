package com.yashmerino.ecommerce.services;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class OutboxService {

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;

    public void saveOutboxEvent(String eventId, String aggregateType, Long aggregateId,
                                String eventType, String topic, String eventKey,
                                Object payload, String idempotencyKey) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            jdbc.update(
                "INSERT INTO outbox_events(event_id, aggregate_type, aggregate_id, event_type, event_version, topic, event_key, payload, idempotency_key, status) VALUES (?,?,?,?,2,?,?,?,?,'PENDING')",
                eventId, aggregateType, aggregateId, eventType, topic, eventKey, payloadJson, idempotencyKey
            );
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize outbox event payload", e);
        }
    }
}
