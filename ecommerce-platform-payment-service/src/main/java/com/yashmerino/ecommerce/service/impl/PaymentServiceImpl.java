package com.yashmerino.ecommerce.service.impl;

import com.yashmerino.ecommerce.kafka.PaymentResultProducer;
import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEvent;
import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEventV2;
import com.yashmerino.ecommerce.model.Payment;
import com.yashmerino.ecommerce.model.operations.PaymentOperation;
import com.yashmerino.ecommerce.model.stripe.StripePaymentResult;
import com.yashmerino.ecommerce.repository.PaymentOperationRepository;
import com.yashmerino.ecommerce.repository.PaymentRepository;
import com.yashmerino.ecommerce.service.InboxService;
import com.yashmerino.ecommerce.service.PaymentService;
import com.yashmerino.ecommerce.service.StripePaymentService;
import com.yashmerino.ecommerce.utils.PaymentStatus;
import jakarta.transaction.Transactional;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;

@Service
@AllArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    private final StripePaymentService stripeService;
    private final PaymentRepository paymentRepository;
    private final PaymentResultProducer resultProducer;
    private final InboxService inboxService;
    private final PaymentOperationRepository paymentOperationRepository;

    @Override
    @Transactional
    public void processPayment(PaymentRequestedEvent event) {
        try {
            StripePaymentResult result =
                    stripeService.charge(
                            event.amount(),
                            "EUR",
                            event.stripeToken()
                    );

            Payment payment = new Payment(
                    event.orderId(),
                    result.getPaymentIntentId(),
                    event.amount(),
                    PaymentStatus.SUCCEEDED
            );

            paymentRepository.save(payment);

            resultProducer.sendSucceeded(event.orderId(), event.paymentId());
            log.info("Payment processed successfully for order with ID {} (server payment ID: {})", event.orderId(), event.paymentId());
        } catch (Exception e) {
            log.error("Payment for order with ID {} couldn't be made.", event.orderId(), e);
            paymentRepository.save(
                    new Payment(event.orderId(), null, event.amount(), PaymentStatus.FAILED)
            );

            resultProducer.sendFailed(event.orderId(), event.paymentId(), e.getMessage());
        }
    }

    @Override
    @Transactional
    public void processPaymentV2(PaymentRequestedEventV2 event) {
        String consumerName = "payment-service";
        if (inboxService.isAlreadyProcessed(consumerName, event.eventId())) {
            log.info("Event {} already processed, skipping", event.eventId());
            return;
        }

        var existing = paymentOperationRepository.findByMainPaymentId(event.paymentId());
        if (existing.isPresent()) {
            PaymentOperation operation = existing.get();
            boolean same = operation.getOrderId().equals(event.orderId())
                    && operation.getAmount().compareTo(new BigDecimal(event.amount())) == 0
                    && operation.getCurrency().equals(event.currency())
                    && operation.getRequestIdempotencyKey().equals(event.idempotencyKey());
            if (!same) throw new IllegalStateException("payment_business_idempotency_conflict");
            inboxService.markProcessed(consumerName, event.eventId(), event.eventType(), event.correlationId(), event.aggregateId());
            return;
        }
        PaymentOperation operation = new PaymentOperation();
        operation.setMainPaymentId(event.paymentId());
        operation.setOrderId(event.orderId());
        operation.setAmount(new BigDecimal(event.amount()));
        operation.setCurrency(event.currency());
        operation.setPaymentMethodRef(event.paymentMethodId());
        operation.setRequestIdempotencyKey(event.idempotencyKey());
        paymentOperationRepository.save(operation);

        inboxService.markProcessed(consumerName, event.eventId(), event.eventType(),
                event.correlationId(), event.aggregateId());
        log.info("V2 payment request received for payment ID {}", event.paymentId());
    }
}
