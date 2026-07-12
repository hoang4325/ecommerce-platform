package com.yashmerino.ecommerce.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.config.OutboxConfig;
import com.yashmerino.ecommerce.model.outbox.OutboxEvent;
import com.yashmerino.ecommerce.repository.OutboxEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private OutboxConfig outboxConfig;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    @Mock
    private PlatformTransactionManager transactionManager;

    private OutboxPublisher outboxPublisher;

    @BeforeEach
    void setUp() {
        outboxPublisher = new OutboxPublisher(outboxEventRepository, outboxConfig,
                kafkaTemplate, new ObjectMapper(), transactionManager);
    }

    @Test
    void testEnqueueSavesEvent() {
        when(outboxEventRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        outboxPublisher.enqueue("event-1", "Payment", 1L, "PaymentResultEventV2",
                "payment.result.v2", "1", new java.util.HashMap<>(), "idem-1");

        verify(outboxEventRepository).save(any());
    }

    @Test
    void testPublishPendingClaimsAndSends() {
        when(outboxConfig.getPublisherId()).thenReturn("publisher-1");
        when(outboxConfig.getPollSize()).thenReturn(10);
        when(outboxConfig.getLeaseSeconds()).thenReturn(30);

        OutboxEvent event = new OutboxEvent();
        event.setId(1L);
        event.setEventId("evt-1");
        event.setTopic("test-topic");
        event.setEventKey("key-1");
        event.setPayload("{}");
        event.setStatus(OutboxEvent.Status.PENDING);

        when(outboxEventRepository.claimPendingEvents(any(), any(), anyInt()))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(null));

        outboxPublisher.publishPending();

        verify(outboxEventRepository).claimPendingEvents(any(), any(), anyInt());
        verify(kafkaTemplate).send(eq("test-topic"), eq("key-1"), eq("{}"));
    }

    @Test
    void testPublishPendingRetriesOnFailure() {
        when(outboxConfig.getPublisherId()).thenReturn("publisher-1");
        when(outboxConfig.getPollSize()).thenReturn(10);
        when(outboxConfig.getLeaseSeconds()).thenReturn(30);

        OutboxEvent event = new OutboxEvent();
        event.setId(2L);
        event.setEventId("evt-2");
        event.setTopic("test-topic");
        event.setEventKey("key-2");
        event.setPayload("{}");
        event.setStatus(OutboxEvent.Status.PENDING);

        when(outboxEventRepository.claimPendingEvents(any(), any(), anyInt()))
                .thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenThrow(new RuntimeException("Kafka error"));

        outboxPublisher.publishPending();

        verify(outboxEventRepository, atLeastOnce()).save(any());
    }
}
