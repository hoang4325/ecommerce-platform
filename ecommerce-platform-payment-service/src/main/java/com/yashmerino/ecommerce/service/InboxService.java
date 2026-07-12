package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.model.processed.ProcessedEvent;
import com.yashmerino.ecommerce.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class InboxService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isAlreadyProcessed(String consumerName, String eventId) {
        return processedEventRepository.existsByConsumerNameAndEventId(consumerName, eventId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(String consumerName, String eventId, String eventType,
                              String correlationId, Long aggregateId) {
        ProcessedEvent processed = new ProcessedEvent();
        processed.setEventId(eventId);
        processed.setConsumerName(consumerName);
        processed.setEventType(eventType);
        processed.setCorrelationId(correlationId);
        processed.setAggregateId(aggregateId);
        processed.setProcessedAt(LocalDateTime.now());
        processedEventRepository.save(processed);
    }
}
