package com.yashmerino.ecommerce.repository;

import com.yashmerino.ecommerce.model.outbox.OutboxEvent;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, Long> {

    @Query(value = "SELECT * FROM outbox_events WHERE ((status IN ('PENDING','RETRY') AND (next_retry_at IS NULL OR next_retry_at <= :now)) OR (status='PROCESSING' AND claimed_at < :leaseExpiry)) ORDER BY created_at LIMIT :limit FOR UPDATE SKIP LOCKED", nativeQuery = true)
    List<OutboxEvent> claimPendingEvents(@Param("now") LocalDateTime now, @Param("leaseExpiry") LocalDateTime leaseExpiry, @Param("limit") int limit);

    boolean existsByIdempotencyKey(String idempotencyKey);
}
