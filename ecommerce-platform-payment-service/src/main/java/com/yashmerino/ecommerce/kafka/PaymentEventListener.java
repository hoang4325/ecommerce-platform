package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEvent;
import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEventV2;
import com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2;
import com.yashmerino.ecommerce.service.PaymentService;
import com.yashmerino.ecommerce.service.RefundWorker;
import lombok.RequiredArgsConstructor;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PaymentEventListener {

    private final PaymentService paymentService;
    private final RefundWorker refundWorker;

    @KafkaListener(
        topics = "payment.requested",
        groupId = "payment-service"
    )
    public void onPaymentRequested(PaymentRequestedEvent event) {
        paymentService.processPayment(event);
    }

    @KafkaListener(topics = "${payment.topics.payment-requested-v2}", groupId = "${spring.kafka.consumer.group-id}")
    public void onPaymentRequestedV2(PaymentRequestedEventV2 event) {
        paymentService.processPaymentV2(event);
    }

    @KafkaListener(topics = "${payment.topics.refund-requested-v2}", groupId = "${spring.kafka.consumer.group-id}")
    public void onRefundRequestedV2(RefundRequestedEventV2 event) {
        refundWorker.receiveRefundRequest(event);
    }
}
