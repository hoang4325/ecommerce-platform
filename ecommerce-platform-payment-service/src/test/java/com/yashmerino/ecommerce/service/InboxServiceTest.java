package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.repository.ProcessedEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InboxServiceTest {

    @Mock
    private ProcessedEventRepository processedEventRepository;

    private InboxService inboxService;

    @BeforeEach
    void setUp() {
        inboxService = new InboxService(processedEventRepository);
    }

    @Test
    void testIsAlreadyProcessedReturnsTrue() {
        when(processedEventRepository.existsByConsumerNameAndEventId("payment-service", "evt-1"))
                .thenReturn(true);

        boolean result = inboxService.isAlreadyProcessed("payment-service", "evt-1");

        assertTrue(result);
    }

    @Test
    void testIsAlreadyProcessedReturnsFalse() {
        when(processedEventRepository.existsByConsumerNameAndEventId("payment-service", "evt-2"))
                .thenReturn(false);

        boolean result = inboxService.isAlreadyProcessed("payment-service", "evt-2");

        assertFalse(result);
    }
}
