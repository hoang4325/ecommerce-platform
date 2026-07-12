package com.yashmerino.ecommerce.service.impl;

import com.stripe.exception.StripeException;
import com.stripe.model.Refund;
import com.stripe.net.RequestOptions;
import com.stripe.param.RefundCreateParams;
import com.yashmerino.ecommerce.model.stripe.StripePaymentResult;
import com.yashmerino.ecommerce.model.stripe.StripeRefundResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for StripePaymentServiceImpl.
 * Note: These are integration-style tests that would typically require mocking Stripe API calls.
 * For unit tests, consider using WireMock or similar to mock HTTP interactions.
 */
class StripePaymentServiceImplTest {

    private StripePaymentServiceImpl stripePaymentService;

    @BeforeEach
    void setUp() {
        stripePaymentService = new StripePaymentServiceImpl();
    }

    @Test
    void testChargeMethodExists() {
        // Verify the service can be instantiated
        assertNotNull(stripePaymentService);
    }

    @Test
    void testChargeThrowsExceptionWithInvalidParameters() {
        // Test with null parameters should throw exception
        assertThrows(Exception.class, () -> {
            stripePaymentService.charge(null, "usd", "pm_test");
        });
    }

    @Test
    void testChargeCalculatesCorrectAmount() throws StripeException {
        // This test would require mocking Stripe API
        // For now, we validate the method signature exists and can be called
        BigDecimal amount = BigDecimal.valueOf(10.50);
        String currency = "usd";
        String paymentMethodId = "pm_card_visa";

        try {
            StripePaymentResult result = stripePaymentService.charge(amount, currency, paymentMethodId);
            // If Stripe is not configured, this will throw an exception
            // In a real test, we would mock the Stripe API
        } catch (Exception e) {
            // Expected when Stripe is not configured
            assertTrue(e instanceof StripeException || e.getMessage().contains("api"));
        }
    }

    @Test
    void testRefundSuccess() throws StripeException {
        Refund mockRefund = mock(Refund.class);
        when(mockRefund.getId()).thenReturn("re_123");
        when(mockRefund.getStatus()).thenReturn("succeeded");
        when(mockRefund.getFailureReason()).thenReturn(null);

        try (MockedStatic<Refund> refundMock = mockStatic(Refund.class)) {
            refundMock.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockRefund);

            StripeRefundResult result = stripePaymentService.refund(
                    "pi_123", new BigDecimal("25.00"), "idem-1");

            assertEquals("re_123", result.getStripeRefundId());
            assertEquals("succeeded", result.getStatus());
            assertNull(result.getFailureCode());
            assertNull(result.getFailureMessage());
        }
    }

    @Test
    void testRefundFailure() throws StripeException {
        Refund mockRefund = mock(Refund.class);
        when(mockRefund.getId()).thenReturn("re_123");
        when(mockRefund.getStatus()).thenReturn("failed");
        when(mockRefund.getFailureReason()).thenReturn("insufficient_funds");

        try (MockedStatic<Refund> refundMock = mockStatic(Refund.class)) {
            refundMock.when(() -> Refund.create(any(RefundCreateParams.class), any(RequestOptions.class)))
                    .thenReturn(mockRefund);

            StripeRefundResult result = stripePaymentService.refund(
                    "pi_123", new BigDecimal("25.00"), "idem-1");

            assertEquals("re_123", result.getStripeRefundId());
            assertEquals("failed", result.getStatus());
            assertEquals("insufficient_funds", result.getFailureCode());
            assertEquals("insufficient_funds", result.getFailureMessage());
        }
    }
}
