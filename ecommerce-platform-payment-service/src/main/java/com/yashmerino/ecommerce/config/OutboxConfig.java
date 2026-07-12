package com.yashmerino.ecommerce.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OutboxConfig {

    @Value("${outbox.poll-size:10}")
    private int pollSize;

    @Value("${outbox.lease-seconds:30}")
    private int leaseSeconds;

    @Value("${outbox.publisher-id:payment-service-${random.value}}")
    private String publisherId;

    public int getPollSize() {
        return pollSize;
    }

    public int getLeaseSeconds() {
        return leaseSeconds;
    }

    public String getPublisherId() {
        return publisherId;
    }
}
