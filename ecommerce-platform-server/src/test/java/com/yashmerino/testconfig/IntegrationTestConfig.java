package com.yashmerino.testconfig;

import org.mockito.Mockito;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import com.yashmerino.ecommerce.kafka.events.NotificationRequestedEvent;
import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEvent;

@Configuration
@EnableAutoConfiguration(exclude = {
        KafkaAutoConfiguration.class
})
@ComponentScan(
        basePackages = "com.yashmerino.ecommerce",
        excludeFilters = {
                @ComponentScan.Filter(
                        type = FilterType.REGEX,
                        pattern = "com\\.yashmerino\\.ecommerce\\.kafka\\..*"
                )
        }
)
public class IntegrationTestConfig {

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
