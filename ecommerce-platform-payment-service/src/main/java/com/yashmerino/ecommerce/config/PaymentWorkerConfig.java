package com.yashmerino.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentWorkerConfig {

    @Value("${payment.worker.poll-size:5}")
    private int pollSize;

    @Value("${payment.worker.lease-seconds:30}")
    private int leaseSeconds;

    @Value("${payment.worker.worker-id:payment-worker-${random.value}}")
    private String workerId;

    @Value("${payment.worker.stripe-timeout-seconds:30}")
    private int stripeTimeoutSeconds;

    public int getPollSize() {
        return pollSize;
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public String getWorkerId() {
        return workerId;
    }

    public int getStripeTimeoutSeconds() {
        return stripeTimeoutSeconds;
    }
}
