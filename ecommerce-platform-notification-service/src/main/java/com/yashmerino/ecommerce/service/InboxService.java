package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.model.processed.ProcessedEvent;
import com.yashmerino.ecommerce.repository.ProcessedEventRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class InboxService {

    private final ProcessedEventRepository processedEventRepository;

    @Transactional(propagation = Propagation.MANDATORY)
    public boolean isAlreadyProcessed(String consumerName, String eventId) {
        return processedEventRepository.existsByConsumerNameAndEventId(consumerName, eventId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void markProcessed(String consumerName, String eventId, String eventType,
                              String correlationId, Long aggregateId) {
        ProcessedEvent processedEvent = ProcessedEvent.builder()
                .consumerName(consumerName)
                .eventId(eventId)
                .eventType(eventType)
                .correlationId(correlationId)
                .aggregateId(aggregateId)
                .processedAt(LocalDateTime.now())
                .build();
        processedEventRepository.save(processedEvent);
        log.info("Marked event {} as processed for consumer {}", eventId, consumerName);
    }
}
