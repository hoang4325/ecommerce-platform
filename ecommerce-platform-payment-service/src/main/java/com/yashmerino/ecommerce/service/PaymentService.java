package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEvent;
import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEventV2;

public interface PaymentService {
    void processPayment(PaymentRequestedEvent event);

    void processPaymentV2(PaymentRequestedEventV2 event);
}

