package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.PaymentReviewRequiredEventV2;
import com.yashmerino.ecommerce.services.CheckoutService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentReviewRequiredV2ConsumerTest {

    @Mock
    private CheckoutService checkoutService;

    @InjectMocks
    private PaymentReviewRequiredV2Consumer consumer;

    @Test
    void onPaymentReviewRequired_DelegatesToCheckoutService() {
        PaymentReviewRequiredEventV2 event = new PaymentReviewRequiredEventV2(
            "evt-1", "PaymentReviewRequiredEventV2", 2,
            "2024-01-01T00:00:00", null, 1L,
            "stripe", null, 100L, 1L,
            "pm_123", "fraud_risk", "redacted", "2024-01-01T00:00:00"
        );

        consumer.onPaymentReviewRequired(event);

        verify(checkoutService).handlePaymentReview(event);
    }
}
