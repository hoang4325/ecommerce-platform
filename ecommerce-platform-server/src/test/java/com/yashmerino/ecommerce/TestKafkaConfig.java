package com.yashmerino.ecommerce;

import com.yashmerino.ecommerce.kafka.events.NotificationRequestedEvent;
import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEvent;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

@TestConfiguration
public class TestKafkaConfig {

    @Bean
    ProducerFactory<String, NotificationRequestedEvent> notificationProducerFactory() {
        return Mockito.mock(ProducerFactory.class);
    }

    @Bean
    KafkaTemplate<String, NotificationRequestedEvent> notificationKafkaTemplate(
            ProducerFactory<String, NotificationRequestedEvent> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    ProducerFactory<String, PaymentRequestedEvent> paymentProducerFactory() {
        return Mockito.mock(ProducerFactory.class);
    }

    @Bean
    KafkaTemplate<String, PaymentRequestedEvent> paymentKafkaTemplate(
            ProducerFactory<String, PaymentRequestedEvent> pf) {
        return new KafkaTemplate<>(pf);
    }

    @Bean
    ProducerFactory<String, Object> outboxProducerFactory() {
        return Mockito.mock(ProducerFactory.class);
    }

    @Bean
    KafkaTemplate<String, Object> outboxKafkaTemplate(
            ProducerFactory<String, Object> pf) {
        return new KafkaTemplate<>(pf);
    }
}
