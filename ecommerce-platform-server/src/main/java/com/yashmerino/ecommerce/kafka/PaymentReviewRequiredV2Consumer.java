package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentReviewRequiredEventV2;
import com.yashmerino.ecommerce.services.CheckoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentReviewRequiredV2Consumer {

    private final CheckoutService checkoutService;

    @KafkaListener(
        topics = "${payment.topics.payment-review-v2}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onPaymentReviewRequired(PaymentReviewRequiredEventV2 event) {
        log.info("Received V2 payment review: paymentId={}, reason={}", event.paymentId(), event.reasonCode());
        checkoutService.handlePaymentReview(event);
    }
}
