package com.yashmerino.ecommerce.repositories;

import com.yashmerino.ecommerce.model.processed.ProcessedEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProcessedEventRepository extends JpaRepository<ProcessedEvent, Long> {
    boolean existsByConsumerNameAndEventId(String consumerName, String eventId);
}
