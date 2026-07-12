package com.yashmerino.ecommerce.services;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Constructor;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherTest {

    @Mock private JdbcTemplate jdbc;
    @Mock private KafkaTemplate<String, Object> kafka;
    @Mock private ObjectMapper objectMapper;
    @Mock private TransactionTemplate transactions;

    private OutboxPublisher publisher;
    private static Class<?> outboxRowClass;

    static {
        for (Class<?> c : OutboxPublisher.class.getDeclaredClasses()) {
            if (c.getSimpleName().equals("OutboxRow")) {
                outboxRowClass = c;
            }
        }
    }

    @BeforeEach
    void setUp() {
        publisher = new OutboxPublisher(jdbc, kafka, objectMapper, transactions);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void executeConsumer() {
        doAnswer(inv -> {
            ((Consumer) inv.getArgument(0)).accept(null);
            return null;
        }).when(transactions).executeWithoutResult(any());
    }

    @SuppressWarnings("unchecked")
    private void returnsRows(List<?> rows) {
        when(transactions.execute(any(TransactionCallback.class))).thenReturn((List<Object>) rows);
    }

    @Test
    void publish_claimsAndPublishesPendingEvents() throws Exception {
        Object row = instantiate(outboxRowClass, 1L, "payment.requested.v2", "key-1", "{\"id\":1}", 0, 10);
        returnsRows(List.of(row));
        executeConsumer();

        when(objectMapper.readTree(anyString())).thenReturn(mock(JsonNode.class));

        var future = mock(CompletableFuture.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenReturn(mock(SendResult.class));

        publisher.publish();

        verify(jdbc).update(contains("status='PUBLISHED'"), anyLong(), anyString());
    }

    @Test
    void publish_retriesOnKafkaFailure() throws Exception {
        Object row = instantiate(outboxRowClass, 1L, "payment.requested.v2", "key-1", "{\"id\":1}", 0, 10);
        returnsRows(List.of(row));
        executeConsumer();

        when(objectMapper.readTree(anyString())).thenReturn(mock(JsonNode.class));

        var future = mock(CompletableFuture.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new RuntimeException("kafka timeout"));

        publisher.publish();

        verify(jdbc).update(contains("SET status=?"), eq("RETRY"), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void publish_deadLetterAfterMaxRetries() throws Exception {
        Object row = instantiate(outboxRowClass, 1L, "payment.requested.v2", "key-1", "{\"id\":1}", 9, 10);
        returnsRows(List.of(row));
        executeConsumer();

        when(objectMapper.readTree(anyString())).thenReturn(mock(JsonNode.class));

        var future = mock(CompletableFuture.class);
        when(kafka.send(anyString(), anyString(), any())).thenReturn(future);
        when(future.get(anyLong(), any(TimeUnit.class))).thenThrow(new RuntimeException("kafka timeout"));

        publisher.publish();

        verify(jdbc).update(contains("SET status=?"), eq("DEAD_LETTER"), any(), anyString(), anyLong(), anyString());
    }

    @Test
    void publish_recoversStaleClaims() {
        executeConsumer();
        returnsRows(List.of());

        publisher.publish();

        verify(jdbc).update(contains("claimed_at<DATE_SUB"));
    }

    private static Object instantiate(Class<?> type, Object... args) {
        try {
            Constructor<?> ctor = type.getDeclaredConstructors()[0];
            ctor.setAccessible(true);
            return ctor.newInstance(args);
        } catch (Exception e) {
            throw new RuntimeException("Cannot instantiate " + type.getSimpleName(), e);
        }
    }
}
