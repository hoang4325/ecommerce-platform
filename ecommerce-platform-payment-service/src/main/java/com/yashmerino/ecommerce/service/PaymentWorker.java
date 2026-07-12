package com.yashmerino.ecommerce.service;

import com.stripe.exception.CardException;
import com.stripe.exception.InvalidRequestException;
import com.yashmerino.ecommerce.config.PaymentWorkerConfig;
import com.yashmerino.ecommerce.kafka.events.PaymentResultEventV2;
import com.yashmerino.ecommerce.kafka.events.PaymentReviewRequiredEventV2;
import com.yashmerino.ecommerce.model.operations.PaymentOperation;
import com.yashmerino.ecommerce.repository.PaymentOperationRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
public class PaymentWorker {
    private final PaymentOperationRepository operations;
    private final PaymentWorkerConfig config;
    private final StripePaymentService stripe;
    private final OutboxPublisher outbox;
    private final TransactionTemplate tx;

    public PaymentWorker(PaymentOperationRepository operations, PaymentWorkerConfig config,
                         StripePaymentService stripe, OutboxPublisher outbox,
                         PlatformTransactionManager transactionManager) {
        this.operations = operations; this.config = config; this.stripe = stripe; this.outbox = outbox;
        this.tx = new TransactionTemplate(transactionManager);
    }

    @Scheduled(fixedDelayString = "${payment.worker.poll-interval-ms:1000}")
    public void processPendingPayments() {
        List<Long> ids = claim();
        for (Long id : ids) execute(id);
    }

    private List<Long> claim() {
        List<Long> claimed = tx.execute(status -> {
            List<PaymentOperation> rows = operations.claimReceivedOperations(
                    LocalDateTime.now().minusSeconds(config.getLeaseSeconds()), config.getPollSize());
            for (PaymentOperation op : rows) {
                if (op.getStripeIdempotencyKey() == null) op.assignStripeIdempotencyKey("payment-" + op.getMainPaymentId());
                op.markProcessing(config.getWorkerId());
            }
            operations.saveAll(rows);
            return rows.stream().map(PaymentOperation::getId).toList();
        });
        return claimed == null ? List.of() : claimed;
    }

    private void execute(Long id) {
        PaymentOperation op = operations.findById(id).orElse(null);
        if (op == null || op.getStatus() != PaymentOperation.Status.PROCESSING) return;
        try {
            var result = stripe.charge(op.getAmount(), op.getCurrency(), op.getPaymentMethodRef(), op.getStripeIdempotencyKey());
            if (!result.isTerminal()) {
                markUnknownAndAlert(id, result.getPaymentIntentId(), "NON_TERMINAL_STRIPE_STATUS");
                return;
            }
            boolean succeeded = "SUCCEEDED".equals(result.getStatus());
            tx.executeWithoutResult(status -> {
                PaymentOperation fresh = operations.findById(id).orElseThrow();
                if (succeeded) fresh.markSucceeded(result.getPaymentIntentId());
                else fresh.markFailed(result.getFailureCode(), safeFailure(result.getFailureMessage()));
                operations.save(fresh);
                String eventId = UUID.randomUUID().toString();
                outbox.enqueue(eventId, "PaymentOperation", fresh.getMainPaymentId(), "PaymentResultEventV2",
                        "payment.result.v2", fresh.getMainPaymentId().toString(),
                        new PaymentResultEventV2(eventId, "PAYMENT_RESULT", 2, LocalDateTime.now().toString(),
                                fresh.getRequestIdempotencyKey(), fresh.getMainPaymentId(), "payment-service",
                                "payment-result:" + fresh.getMainPaymentId() + ":" + (succeeded ? "SUCCEEDED" : "FAILED"),
                                fresh.getMainPaymentId(), fresh.getOrderId(), fresh.getAmount().toPlainString(), fresh.getCurrency(),
                                result.getPaymentIntentId(), succeeded ? "SUCCEEDED" : "FAILED",
                                succeeded ? null : result.getFailureCode(), succeeded ? null : safeFailure(result.getFailureMessage())),
                        "payment-result:" + fresh.getMainPaymentId());
            });
        } catch (CardException | InvalidRequestException terminal) {
            markTerminalFailure(id, terminal.getClass().getSimpleName());
        } catch (Exception uncertain) {
            markUnknownAndAlert(id, op.getStripePaymentIntentId(), "STRIPE_OUTCOME_UNKNOWN");
        }
    }

    private void markTerminalFailure(Long id, String code) {
        tx.executeWithoutResult(status -> {
            PaymentOperation fresh = operations.findById(id).orElseThrow();
            fresh.markFailed(code, "Payment was declined"); operations.save(fresh);
            String eventId = UUID.randomUUID().toString();
            outbox.enqueue(eventId, "PaymentOperation", fresh.getMainPaymentId(), "PaymentResultEventV2", "payment.result.v2",
                    fresh.getMainPaymentId().toString(),
                    new PaymentResultEventV2(eventId, "PAYMENT_RESULT", 2, LocalDateTime.now().toString(), fresh.getRequestIdempotencyKey(),
                            fresh.getMainPaymentId(), "payment-service", "payment-result:" + fresh.getMainPaymentId() + ":FAILED",
                            fresh.getMainPaymentId(), fresh.getOrderId(), fresh.getAmount().toPlainString(), fresh.getCurrency(), null,
                            "FAILED", code, "Payment was declined"), "payment-result:" + fresh.getMainPaymentId());
        });
    }

    private void markUnknownAndAlert(Long id, String externalId, String reason) {
        tx.executeWithoutResult(status -> {
            PaymentOperation fresh = operations.findById(id).orElseThrow();
            if (externalId != null) fresh.setStripePaymentIntentId(externalId);
            fresh.markUnknown(); operations.save(fresh);
            String eventId = UUID.randomUUID().toString();
            if (!outbox.exists("payment-review:" + fresh.getMainPaymentId())) {
                outbox.enqueue(eventId, "PaymentOperation", fresh.getMainPaymentId(), "PaymentReviewRequiredEventV2",
                        "payment.review.required.v2", fresh.getMainPaymentId().toString(),
                        new PaymentReviewRequiredEventV2(eventId, "PAYMENT_REVIEW_REQUIRED", 2, LocalDateTime.now().toString(),
                                fresh.getRequestIdempotencyKey(), fresh.getMainPaymentId(), "payment-service",
                                "payment-review:" + fresh.getMainPaymentId(), fresh.getMainPaymentId(), fresh.getOrderId(), externalId,
                                reason, "Payment requires reconciliation", LocalDateTime.now().toString()),
                        "payment-review:" + fresh.getMainPaymentId());
            }
        });
    }

    private String safeFailure(String message) { return message == null ? "Payment failed" : "Payment failed"; }
}
