package com.yashmerino.ecommerce.service;

import com.stripe.exception.InvalidRequestException;
import com.yashmerino.ecommerce.config.PaymentWorkerConfig;
import com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2;
import com.yashmerino.ecommerce.kafka.events.RefundResultEventV2;
import com.yashmerino.ecommerce.model.operations.RefundOperation;
import com.yashmerino.ecommerce.repository.RefundOperationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class RefundWorker {
    private final RefundOperationRepository operations;
    private final PaymentWorkerConfig config;
    private final StripePaymentService stripe;
    private final OutboxPublisher outbox;
    private final InboxService inbox;
    private final TransactionTemplate tx;

    public RefundWorker(RefundOperationRepository operations, PaymentWorkerConfig config,
                        StripePaymentService stripe, OutboxPublisher outbox, InboxService inbox,
                        PlatformTransactionManager transactionManager) {
        this.operations = operations; this.config = config; this.stripe = stripe; this.outbox = outbox; this.inbox = inbox;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Transactional
    public void receiveRefundRequest(RefundRequestedEventV2 event) {
        if (inbox.isAlreadyProcessed("payment-service", event.eventId())) return;
        var existing = operations.findByRefundId(event.refundId());
        if (existing.isPresent()) {
            RefundOperation operation = existing.get();
            boolean same = operation.getMainPaymentId().equals(event.paymentId())
                    && operation.getAmount().compareTo(new BigDecimal(event.amount())) == 0
                    && operation.getCurrency().equals(event.currency())
                    && operation.getRequestIdempotencyKey().equals(event.requestIdempotencyKey());
            if (!same) throw new IllegalStateException("refund_business_idempotency_conflict");
            inbox.markProcessed("payment-service", event.eventId(), event.eventType(), event.correlationId(), event.aggregateId());
            return;
        }
        RefundOperation operation = new RefundOperation();
        operation.setRefundId(event.refundId()); operation.setMainPaymentId(event.paymentId()); operation.setOrderId(event.orderId());
        operation.setAmount(new BigDecimal(event.amount())); operation.setCurrency(event.currency());
        operation.setExternalPaymentId(event.externalPaymentId()); operation.setRequestIdempotencyKey(event.requestIdempotencyKey());
        operations.save(operation);
        inbox.markProcessed("payment-service", event.eventId(), event.eventType(), event.correlationId(), event.aggregateId());
    }

    @Scheduled(fixedDelayString = "${payment.worker.poll-interval-ms:1000}")
    public void processPendingRefunds() {
        for (Long id : claim()) execute(id);
    }

    private List<Long> claim() {
        List<Long> ids = tx.execute(status -> {
            List<RefundOperation> rows = operations.claimReceivedOperations(LocalDateTime.now().minusSeconds(config.getLeaseSeconds()), config.getPollSize());
            for (RefundOperation op : rows) {
                if (op.getStripeIdempotencyKey() == null) op.assignStripeIdempotencyKey("refund-" + op.getRefundId());
                op.markProcessing("refund-" + config.getWorkerId());
            }
            operations.saveAll(rows);
            return rows.stream().map(RefundOperation::getId).toList();
        });
        return ids == null ? List.of() : ids;
    }

    private void execute(Long id) {
        RefundOperation op = operations.findById(id).orElse(null);
        if (op == null || op.getStatus() != RefundOperation.Status.PROCESSING) return;
        try {
            var result = stripe.refund(op.getExternalPaymentId(), op.getAmount(), op.getStripeIdempotencyKey());
            if ("succeeded".equalsIgnoreCase(result.getStatus())) terminal(id, true, result.getStripeRefundId(), null);
            else if ("failed".equalsIgnoreCase(result.getStatus()) || "canceled".equalsIgnoreCase(result.getStatus()))
                terminal(id, false, result.getStripeRefundId(), result.getFailureCode());
            else unknown(id);
        } catch (InvalidRequestException terminal) {
            terminal(id, false, null, terminal.getClass().getSimpleName());
        } catch (Exception uncertain) {
            unknown(id);
        }
    }

    private void terminal(Long id, boolean succeeded, String stripeRefundId, String failureCode) {
        tx.executeWithoutResult(status -> {
            RefundOperation fresh = operations.findById(id).orElseThrow();
            if (succeeded) fresh.markSucceeded(stripeRefundId); else fresh.markFailed(failureCode, "Refund failed");
            operations.save(fresh);
            String eventId = UUID.randomUUID().toString();
            String terminalStatus = succeeded ? "SUCCEEDED" : "FAILED";
            outbox.enqueue(eventId, "RefundOperation", fresh.getRefundId(), "RefundResultEventV2", "payment.refund.result.v2",
                    fresh.getMainPaymentId().toString(),
                    new RefundResultEventV2(eventId, "REFUND_RESULT", 2, LocalDateTime.now().toString(), fresh.getRequestIdempotencyKey(),
                            fresh.getRefundId(), "payment-service", "refund-result:" + fresh.getRefundId() + ":" + terminalStatus,
                            fresh.getRefundId(), fresh.getOrderId(), fresh.getMainPaymentId(), fresh.getExternalPaymentId(), stripeRefundId,
                            fresh.getAmount().toPlainString(), fresh.getCurrency(), terminalStatus,
                            succeeded ? null : failureCode, succeeded ? null : "Refund failed"),
                    "refund-result:" + fresh.getRefundId());
        });
    }

    private void unknown(Long id) {
        tx.executeWithoutResult(status -> {
            RefundOperation fresh = operations.findById(id).orElseThrow();
            fresh.markUnknown(); operations.save(fresh);
        });
    }
}
