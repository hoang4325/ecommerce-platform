package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentResultEventV2;
import com.yashmerino.ecommerce.services.CheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentResultV2Consumer {

    private final CheckoutService checkoutService;

    @KafkaListener(
        topics = "${payment.topics.payment-result-v2}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentResult(PaymentResultEventV2 event) {
        log.info("Received V2 payment result: paymentId={}, status={}", event.paymentId(), event.status());
        checkoutService.processPaymentResultV2(event);
    }
}
