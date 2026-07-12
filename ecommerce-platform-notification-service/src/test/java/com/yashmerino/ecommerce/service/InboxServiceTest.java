package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.model.processed.ProcessedEvent;
import com.yashmerino.ecommerce.repository.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private InboxService inboxService;

    @Test
    void testIsAlreadyProcessedReturnsTrueWhenExists() {
        when(processedEventRepository.existsByConsumerNameAndEventId("consumer1", "event1"))
                .thenReturn(true);

        boolean result = inboxService.isAlreadyProcessed("consumer1", "event1");

        assertTrue(result);
        verify(processedEventRepository, times(1))
                .existsByConsumerNameAndEventId("consumer1", "event1");
    }

    @Test
    void testIsAlreadyProcessedReturnsFalseWhenNotExists() {
        when(processedEventRepository.existsByConsumerNameAndEventId("consumer2", "event2"))
                .thenReturn(false);

        boolean result = inboxService.isAlreadyProcessed("consumer2", "event2");

        assertFalse(result);
        verify(processedEventRepository, times(1))
                .existsByConsumerNameAndEventId("consumer2", "event2");
    }

    @Test
    void testMarkProcessedSavesAndReturns() {
        ProcessedEvent savedEvent = ProcessedEvent.builder()
                .consumerName("consumer1")
                .eventId("event1")
                .eventType("ORDER_CREATED")
                .correlationId("corr-1")
                .aggregateId(123L)
                .build();
        when(processedEventRepository.save(any(ProcessedEvent.class))).thenReturn(savedEvent);

        inboxService.markProcessed("consumer1", "event1", "ORDER_CREATED", "corr-1", 123L);

        verify(processedEventRepository, times(1)).save(any(ProcessedEvent.class));
    }
}
