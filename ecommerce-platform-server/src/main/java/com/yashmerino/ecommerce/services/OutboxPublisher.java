package com.yashmerino.ecommerce.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
public class OutboxPublisher {
    private final JdbcTemplate jdbc;
    private final KafkaTemplate<String, Object> kafka;
    private final ObjectMapper objectMapper;
    private final TransactionTemplate transactions;
    private final String workerId = UUID.randomUUID().toString();

    @Scheduled(fixedDelayString = "${outbox.publish-delay-ms:1000}")
    public void publish() {
        recoverStaleClaims();
        for (OutboxRow row : claim()) send(row);
    }

    private List<OutboxRow> claim() {
        return transactions.execute(status -> {
            List<OutboxRow> rows = jdbc.query("SELECT id,topic,event_key,payload,retry_count,max_retries FROM outbox_events " +
                            "WHERE status IN ('PENDING','RETRY') AND (next_retry_at IS NULL OR next_retry_at<=CURRENT_TIMESTAMP(6)) " +
                            "ORDER BY id LIMIT 50 FOR UPDATE SKIP LOCKED",
                    (rs, n) -> new OutboxRow(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getInt(5), rs.getInt(6)));
            for (OutboxRow row : rows) jdbc.update("UPDATE outbox_events SET status='PROCESSING',claimed_by=?,claimed_at=CURRENT_TIMESTAMP(6) WHERE id=?", workerId, row.id());
            return rows;
        });
    }

    private void send(OutboxRow row) {
        try {
            Object payload = objectMapper.readTree(row.payload());
            kafka.send(row.topic(), row.key(), payload).get(15, TimeUnit.SECONDS);
            transactions.executeWithoutResult(s -> jdbc.update("UPDATE outbox_events SET status='PUBLISHED',published_at=CURRENT_TIMESTAMP(6),claimed_by=NULL,claimed_at=NULL WHERE id=? AND status='PROCESSING' AND claimed_by=?", row.id(), workerId));
        } catch (Exception failure) {
            String message = failure.getClass().getSimpleName();
            transactions.executeWithoutResult(s -> jdbc.update("UPDATE outbox_events SET status=?,retry_count=retry_count+1,next_retry_at=DATE_ADD(CURRENT_TIMESTAMP(6),INTERVAL ? SECOND),last_error=?,claimed_by=NULL,claimed_at=NULL WHERE id=? AND status='PROCESSING' AND claimed_by=?",
                    row.retries() + 1 >= row.maxRetries() ? "DEAD_LETTER" : "RETRY",
                    Math.min(300, 1 << Math.min(8, row.retries())), message, row.id(), workerId));
        }
    }

    private void recoverStaleClaims() {
        transactions.executeWithoutResult(s -> jdbc.update("UPDATE outbox_events SET status='RETRY',claimed_by=NULL,claimed_at=NULL,retry_count=retry_count+1,next_retry_at=CURRENT_TIMESTAMP(6) WHERE status='PROCESSING' AND claimed_at<DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL 1 MINUTE)"));
    }

    private record OutboxRow(long id, String topic, String key, String payload, int retries, int maxRetries) {}
}
