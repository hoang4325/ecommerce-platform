package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentResultEventV2;
import com.yashmerino.ecommerce.services.CheckoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentResultV2ConsumerTest {

    @Mock
    private CheckoutService checkoutService;

    @InjectMocks
    private PaymentResultV2Consumer consumer;

    @Test
    void onPaymentResult_WithSucceededStatus_DelegatesToCheckoutService() {
        PaymentResultEventV2 event = new PaymentResultEventV2(
            "evt-1", "PaymentResultEventV2", 2,
            "2024-01-01T00:00:00", null, 1L,
            "stripe", null, 100L, 1L,
            "100.00", "EUR", "pm_123",
            "SUCCEEDED", null, null
        );

        consumer.onPaymentResult(event);

        verify(checkoutService).processPaymentResultV2(event);
    }

    @Test
    void onPaymentResult_WithFailedStatus_DelegatesToCheckoutService() {
        PaymentResultEventV2 event = new PaymentResultEventV2(
            "evt-2", "PaymentResultEventV2", 2,
            "2024-01-01T00:00:00", null, 1L,
            "stripe", null, 100L, 1L,
            "100.00", "EUR", "pm_123",
            "FAILED", "card_declined", null
        );

        consumer.onPaymentResult(event);

        verify(checkoutService).processPaymentResultV2(event);
    }
}
