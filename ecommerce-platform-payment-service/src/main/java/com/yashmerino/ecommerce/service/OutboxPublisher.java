package com.yashmerino.ecommerce.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.config.OutboxConfig;
import com.yashmerino.ecommerce.model.outbox.OutboxEvent;
import com.yashmerino.ecommerce.repository.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Service
public class OutboxPublisher {

    private static final Logger log = LoggerFactory.getLogger(OutboxPublisher.class);

    private final OutboxEventRepository outboxEventRepository;
    private final OutboxConfig outboxConfig;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactionTemplate;

    public OutboxPublisher(OutboxEventRepository outboxEventRepository,
                           OutboxConfig outboxConfig,
                           KafkaTemplate<String, String> kafkaTemplate,
                           ObjectMapper objectMapper,
                           PlatformTransactionManager transactionManager) {
        this.outboxEventRepository = outboxEventRepository;
        this.outboxConfig = outboxConfig;
        this.kafkaTemplate = kafkaTemplate;
        this.objectMapper = objectMapper;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    public void enqueue(String eventId, String aggregateType, Long aggregateId,
                        String eventType, String topic, String eventKey,
                        Object payload, String idempotencyKey) {
        try {
            String payloadJson = objectMapper.writeValueAsString(payload);
            OutboxEvent event = new OutboxEvent();
            event.setEventId(eventId);
            event.setAggregateType(aggregateType);
            event.setAggregateId(aggregateId);
            event.setEventType(eventType);
            event.setEventVersion(2);
            event.setTopic(topic);
            event.setEventKey(eventKey);
            event.setPayload(payloadJson);
            event.setIdempotencyKey(idempotencyKey);
            outboxEventRepository.save(event);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize payload for outbox event", e);
        }
    }

    public boolean exists(String idempotencyKey) {
        return outboxEventRepository.existsByIdempotencyKey(idempotencyKey);
    }

    @Scheduled(fixedDelayString = "${outbox.poll-interval-ms:2000}")
    public void publishPending() {
        String publisherId = outboxConfig.getPublisherId();
        int pollSize = outboxConfig.getPollSize();
        int leaseSeconds = outboxConfig.getLeaseSeconds();

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime leaseExpiry = now.minusSeconds(leaseSeconds);

        List<OutboxEvent> events = transactionTemplate.execute(status -> {
            List<OutboxEvent> claimed = outboxEventRepository.claimPendingEvents(now, leaseExpiry, pollSize);
            claimed.forEach(event -> {
                event.markProcessing(publisherId);
                outboxEventRepository.save(event);
            });
            return claimed;
        });

        if (events == null) return;
        for (OutboxEvent event : events) {

            try {
                kafkaTemplate.send(event.getTopic(), event.getEventKey(), event.getPayload()).get(10, TimeUnit.SECONDS);
                transactionTemplate.executeWithoutResult(status -> {
                    event.markPublished();
                    outboxEventRepository.save(event);
                });
            } catch (Exception e) {
                log.error("Failed to publish outbox event {}", event.getEventId());
                transactionTemplate.executeWithoutResult(status -> {
                    event.markRetry(e.getClass().getSimpleName());
                    outboxEventRepository.save(event);
                });
            }
        }
    }
}
