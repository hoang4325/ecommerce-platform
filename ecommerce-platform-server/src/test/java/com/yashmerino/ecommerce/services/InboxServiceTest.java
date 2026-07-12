package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.model.processed.ProcessedEvent;
import com.yashmerino.ecommerce.repositories.ProcessedEventRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    @InjectMocks
    private InboxService inboxService;

    @Test
    void isAlreadyProcessed_WhenEventExists_ReturnsTrue() {
        when(processedEventRepository.existsByConsumerNameAndEventId("main-server", "event-1"))
            .thenReturn(true);

        boolean result = inboxService.isAlreadyProcessed("main-server", "event-1");

        assertTrue(result);
    }

    @Test
    void isAlreadyProcessed_WhenEventDoesNotExist_ReturnsFalse() {
        when(processedEventRepository.existsByConsumerNameAndEventId("main-server", "event-1"))
            .thenReturn(false);

        boolean result = inboxService.isAlreadyProcessed("main-server", "event-1");

        assertFalse(result);
    }

    @Test
    void markProcessed_SavesProcessedEvent() {
        inboxService.markProcessed("main-server", "event-1", "RefundResultEventV2", "corr-1", 42L);

        ArgumentCaptor<ProcessedEvent> captor = ArgumentCaptor.forClass(ProcessedEvent.class);
        verify(processedEventRepository).save(captor.capture());

        ProcessedEvent saved = captor.getValue();
        assertEquals("event-1", saved.getEventId());
        assertEquals("main-server", saved.getConsumerName());
        assertEquals("RefundResultEventV2", saved.getEventType());
        assertEquals("corr-1", saved.getCorrelationId());
        assertEquals(42L, saved.getAggregateId());
        assertNotNull(saved.getProcessedAt());
    }
}
