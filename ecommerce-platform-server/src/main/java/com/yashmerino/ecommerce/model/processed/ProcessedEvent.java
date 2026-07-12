package com.yashmerino.ecommerce.model.processed;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Getter
@Setter
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36)
    private String eventId;

    @Column(name = "consumer_name", nullable = false, length = 255)
    private String consumerName;

    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;

    public ProcessedEvent(String eventId, String consumerName, String eventType, String correlationId, Long aggregateId, LocalDateTime processedAt) {
        this.eventId = eventId;
        this.consumerName = consumerName;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.aggregateId = aggregateId;
        this.processedAt = processedAt;
    }
}
