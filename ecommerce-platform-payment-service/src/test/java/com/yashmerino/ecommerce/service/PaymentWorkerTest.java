package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.config.PaymentWorkerConfig;
import com.yashmerino.ecommerce.model.operations.PaymentOperation;
import com.yashmerino.ecommerce.model.stripe.StripePaymentResult;
import com.yashmerino.ecommerce.repository.PaymentOperationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.junit.jupiter.api.Assertions.*;

@ExtendWith(MockitoExtension.class)
class PaymentWorkerTest {

    @Mock
    private PaymentOperationRepository paymentOperationRepository;

    @Mock
    private PaymentWorkerConfig paymentWorkerConfig;

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private PlatformTransactionManager transactionManager;

    private PaymentWorker paymentWorker;

    @BeforeEach
    void setUp() {
        paymentWorker = new PaymentWorker(
                paymentOperationRepository, paymentWorkerConfig, stripePaymentService,
                outboxPublisher, transactionManager);
    }

    @Test
    void testProcessPendingPaymentsClaimsAndProcessesSuccess() throws Exception {
        when(paymentWorkerConfig.getWorkerId()).thenReturn("worker-1");
        when(paymentWorkerConfig.getPollSize()).thenReturn(5);
        when(paymentWorkerConfig.getLeaseSeconds()).thenReturn(30);

        PaymentOperation op = new PaymentOperation();
        op.setId(1L);
        op.setMainPaymentId(100L);
        op.setOrderId(1L);
        op.setAmount(new java.math.BigDecimal("99.99"));
        op.setCurrency("EUR");
        op.setPaymentMethodRef("pm_123");
        op.setStatus(PaymentOperation.Status.RECEIVED);

        when(paymentOperationRepository.claimReceivedOperations(any(), anyInt()))
                .thenReturn(java.util.List.of(op));
        when(paymentOperationRepository.findById(1L))
                .thenReturn(java.util.Optional.of(op));

        StripePaymentResult stripeResult =
                new StripePaymentResult("pi_123", "SUCCEEDED");
        when(stripePaymentService.charge(any(), anyString(), anyString(), anyString()))
                .thenReturn(stripeResult);

        paymentWorker.processPendingPayments();

        verify(paymentOperationRepository).claimReceivedOperations(any(), anyInt());
        verify(stripePaymentService).charge(any(), eq("EUR"), eq("pm_123"), eq("payment-100"));
        assertEquals(PaymentOperation.Status.SUCCEEDED, op.getStatus());
        assertNull(op.getPaymentMethodRef());
        verify(outboxPublisher).enqueue(anyString(), eq("PaymentOperation"), eq(100L), eq("PaymentResultEventV2"),
                eq("payment.result.v2"), eq("100"), any(), eq("payment-result:100"));
    }

    @Test
    void testProcessPendingPaymentsHandlesFailure() throws Exception {
        when(paymentWorkerConfig.getWorkerId()).thenReturn("worker-1");
        when(paymentWorkerConfig.getPollSize()).thenReturn(5);
        when(paymentWorkerConfig.getLeaseSeconds()).thenReturn(30);

        PaymentOperation op = new PaymentOperation();
        op.setId(2L);
        op.setMainPaymentId(200L);
        op.setOrderId(2L);
        op.setAmount(new java.math.BigDecimal("50.00"));
        op.setCurrency("EUR");
        op.setPaymentMethodRef("pm_456");
        op.setStatus(PaymentOperation.Status.RECEIVED);

        when(paymentOperationRepository.claimReceivedOperations(any(), anyInt()))
                .thenReturn(java.util.List.of(op));
        when(paymentOperationRepository.findById(2L))
                .thenReturn(java.util.Optional.of(op));

        StripePaymentResult stripeResult =
                new StripePaymentResult("pi_456", "FAILED", "card_declined", "Card declined");
        when(stripePaymentService.charge(any(), anyString(), anyString(), anyString()))
                .thenReturn(stripeResult);

        paymentWorker.processPendingPayments();

        verify(paymentOperationRepository).claimReceivedOperations(any(), anyInt());
        assertEquals(PaymentOperation.Status.FAILED, op.getStatus());
        assertEquals("payment-200", op.getStripeIdempotencyKey());
    }

    @Test
    void uncertainStripeFailureRemainsUnknownAndUsesStableKeyForRecovery() throws Exception {
        when(paymentWorkerConfig.getWorkerId()).thenReturn("worker-1");
        when(paymentWorkerConfig.getPollSize()).thenReturn(5);
        when(paymentWorkerConfig.getLeaseSeconds()).thenReturn(30);
        PaymentOperation op = new PaymentOperation(); op.setId(3L); op.setMainPaymentId(300L); op.setOrderId(3L);
        op.setAmount(new java.math.BigDecimal("10.00")); op.setCurrency("EUR"); op.setPaymentMethodRef("pm_789");
        when(paymentOperationRepository.claimReceivedOperations(any(), anyInt())).thenReturn(List.of(op));
        when(paymentOperationRepository.findById(3L)).thenReturn(Optional.of(op));
        when(stripePaymentService.charge(any(), anyString(), anyString(), anyString())).thenThrow(new RuntimeException("timeout"));
        when(outboxPublisher.exists("payment-review:300")).thenReturn(false);

        paymentWorker.processPendingPayments();

        assertEquals(PaymentOperation.Status.UNKNOWN, op.getStatus());
        assertEquals("payment-300", op.getStripeIdempotencyKey());
        verify(outboxPublisher).enqueue(anyString(), anyString(), eq(300L), eq("PaymentReviewRequiredEventV2"),
                anyString(), eq("300"), any(), eq("payment-review:300"));
    }
}
