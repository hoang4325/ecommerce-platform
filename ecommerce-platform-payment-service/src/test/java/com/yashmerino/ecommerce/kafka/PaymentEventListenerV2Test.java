package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentRequestedEventV2;
import com.yashmerino.ecommerce.service.PaymentService;
import com.yashmerino.ecommerce.service.RefundWorker;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentEventListenerV2Test {

    @Mock
    private PaymentService paymentService;

    @Mock
    private RefundWorker refundWorker;

    private PaymentEventListener listener;

    @BeforeEach
    void setUp() {
        listener = new PaymentEventListener(paymentService, refundWorker);
    }

    @Test
    void testOnPaymentRequestedV2DelegatesToService() {
        PaymentRequestedEventV2 event =
                new PaymentRequestedEventV2(
                        "evt-1", "PAYMENT_REQUESTED", 2, "2026-01-01T00:00:00",
                        "corr-1", 1L, "main-server", "idem-1",
                        100L, 1L, "99.99", "EUR", "pm_123"
                );

        listener.onPaymentRequestedV2(event);

        verify(paymentService, times(1)).processPaymentV2(event);
    }
}
