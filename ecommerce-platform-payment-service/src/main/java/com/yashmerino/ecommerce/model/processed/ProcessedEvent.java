package com.yashmerino.ecommerce.model.processed;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "processed_events")
@Getter
@Setter
@NoArgsConstructor
public class ProcessedEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", length = 36)
    private String eventId;

    @Column(name = "consumer_name", length = 255)
    private String consumerName;

    @Column(name = "event_type")
    private String eventType;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "aggregate_id")
    private Long aggregateId;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    private void onCreate() {
        processedAt = LocalDateTime.now();
    }
}
