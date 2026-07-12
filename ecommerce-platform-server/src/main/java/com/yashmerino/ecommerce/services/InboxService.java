package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.model.processed.ProcessedEvent;
import com.yashmerino.ecommerce.repositories.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class InboxService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isAlreadyProcessed(String consumerName, String eventId) {
        return processedEventRepository.existsByConsumerNameAndEventId(consumerName, eventId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(String consumerName, String eventId, String eventType, String correlationId, Long aggregateId) {
        processedEventRepository.save(new ProcessedEvent(eventId, consumerName, eventType, correlationId, aggregateId, LocalDateTime.now()));
    }
}
