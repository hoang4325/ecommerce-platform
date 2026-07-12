package com.yashmerino.ecommerce.service;

import com.yashmerino.ecommerce.config.PaymentWorkerConfig;
import com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2;
import com.yashmerino.ecommerce.model.operations.RefundOperation;
import com.yashmerino.ecommerce.model.stripe.StripeRefundResult;
import com.yashmerino.ecommerce.repository.RefundOperationRepository;
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
class RefundWorkerTest {

    @Mock
    private RefundOperationRepository refundOperationRepository;

    @Mock
    private PaymentWorkerConfig paymentWorkerConfig;

    @Mock
    private StripePaymentService stripePaymentService;

    @Mock
    private OutboxPublisher outboxPublisher;

    @Mock
    private InboxService inboxService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private RefundWorker refundWorker;

    @BeforeEach
    void setUp() {
        refundWorker = new RefundWorker(
                refundOperationRepository, paymentWorkerConfig, stripePaymentService,
                outboxPublisher, inboxService, transactionManager);
    }

    @Test
    void testReceiveRefundRequestCreatesOperation() {
        com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2 event =
                new com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2(
                        "evt-1", "REFUND_REQUESTED", 2, "2026-01-01T00:00:00",
                        "corr-1", 1L, "main-server", "idem-1",
                        500L, 10L, 100L, "pi_123",
                        "25.00", "EUR", "not needed", "admin", "req-idem-1"
                );

        when(refundOperationRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        refundWorker.receiveRefundRequest(event);

        verify(refundOperationRepository, times(1)).save(any());
    }

    @Test
    void testReceiveRefundRequestSkipsDuplicate() {
        com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2 event =
                new com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2(
                        "evt-dup", "REFUND_REQUESTED", 2, "2026-01-01T00:00:00",
                        "corr-2", 2L, "main-server", "idem-2",
                        600L, 20L, 200L, "pi_200",
                        "30.00", "USD", "duplicate", "admin", "req-idem-2"
                );

        when(inboxService.isAlreadyProcessed("payment-service", "evt-dup"))
                .thenReturn(true);

        refundWorker.receiveRefundRequest(event);

        verify(refundOperationRepository, never()).save(any());
    }

    @Test
    void testProcessPendingRefundsClaimsAndProcesses() throws Exception {
        com.yashmerino.ecommerce.model.operations.RefundOperation op = new com.yashmerino.ecommerce.model.operations.RefundOperation();
        op.setId(1L);
        op.setRefundId(500L);
        op.setMainPaymentId(100L);
        op.setOrderId(1L);
        op.setAmount(new java.math.BigDecimal("25.00"));
        op.setCurrency("EUR");
        op.setExternalPaymentId("pi_123");
        op.setStatus(com.yashmerino.ecommerce.model.operations.RefundOperation.Status.RECEIVED);

        when(refundOperationRepository.claimReceivedOperations(any(), anyInt()))
                .thenReturn(java.util.List.of(op));
        when(refundOperationRepository.findById(1L))
                .thenReturn(java.util.Optional.of(op));

        com.yashmerino.ecommerce.model.stripe.StripeRefundResult refundResult =
                new com.yashmerino.ecommerce.model.stripe.StripeRefundResult("re_123", "succeeded");
        when(stripePaymentService.refund(anyString(), any(), anyString()))
                .thenReturn(refundResult);

        refundWorker.processPendingRefunds();

        verify(refundOperationRepository).claimReceivedOperations(any(), anyInt());
        verify(stripePaymentService).refund(eq("pi_123"), any(), eq("refund-500"));
        assertEquals(RefundOperation.Status.SUCCEEDED, op.getStatus());
        verify(outboxPublisher).enqueue(anyString(), eq("RefundOperation"), eq(500L), eq("RefundResultEventV2"),
                eq("payment.refund.result.v2"), eq("100"), any(), eq("refund-result:500"));
    }
}
