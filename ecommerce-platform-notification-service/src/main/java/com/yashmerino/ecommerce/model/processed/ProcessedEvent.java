package com.yashmerino.ecommerce.model.processed;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@AllArgsConstructor
@NoArgsConstructor
@Builder
@Getter
@Setter
@Entity
@Table(name = "processed_events")
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36, nullable = false)
    private String eventId;

    @Column(name = "consumer_name", length = 255, nullable = false)
    private String consumerName;

    @Column(name = "event_type", length = 100, nullable = false)
    private String eventType;

    @Column(name = "correlation_id", length = 255)
    private String correlationId;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "processed_at", nullable = false)
    private LocalDateTime processedAt;
}
