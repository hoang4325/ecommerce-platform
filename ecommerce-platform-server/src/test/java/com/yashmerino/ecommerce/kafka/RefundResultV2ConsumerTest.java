package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.RefundResultEventV2;
import com.yashmerino.ecommerce.model.Payment;
import com.yashmerino.ecommerce.model.domain.Refund;
import com.yashmerino.ecommerce.repositories.OrderRepository;
import com.yashmerino.ecommerce.repositories.PaymentRepository;
import com.yashmerino.ecommerce.repositories.RefundRepository;
import com.yashmerino.ecommerce.services.InboxService;
import org.springframework.jdbc.core.JdbcTemplate;
import com.yashmerino.ecommerce.utils.OrderStatus;
import com.yashmerino.ecommerce.utils.PaymentStatus;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundResultV2ConsumerTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InboxService inboxService;

    @Mock
    private JdbcTemplate jdbc;

    @InjectMocks
    private RefundResultV2Consumer consumer;

    private RefundResultEventV2 createEvent(Long refundId, String status) {
        return new RefundResultEventV2(
            "evt-1", "RefundResultEventV2", 2,
            "2024-01-01T00:00:00", null, refundId,
            "stripe", "ik-1", refundId, 1L, 10L,
            "pm_123", "sr_123", "100.00", "EUR",
            status, null, null
        );
    }

    @Test
    void onRefundResult_Succeeded_UpdatesOrderAndPayment() {
        RefundResultEventV2 event = createEvent(1L, "SUCCEEDED");
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setOrderId(1L);
        refund.setPaymentId(10L);
        refund.setVersion(1L);
        refund.setAmount(new java.math.BigDecimal("100.00"));
        refund.setCurrency("EUR");

        Payment payment = new Payment();
        payment.setId(10L);
        payment.setVersion(1L);
        payment.setExternalPaymentId("pm_123");

        when(inboxService.isAlreadyProcessed("main-server", "evt-1")).thenReturn(false);
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(orderRepository.updateOrderStatusAndVersion(1L, OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED, 1L)).thenReturn(1);
        when(paymentRepository.updateStatusAndVersion(10L, PaymentStatus.REFUND_PENDING, PaymentStatus.REFUNDED, 1L)).thenReturn(1);

        consumer.onRefundResult(event);

        assertEquals(Refund.Status.SUCCEEDED, refund.getStatus());
        assertEquals("sr_123", refund.getExternalRefundId());
        verify(refundRepository).save(refund);
        verify(inboxService).markProcessed("main-server", "evt-1", "RefundResultEventV2", null, 1L);
    }

    @Test
    void onRefundResult_Failed_UpdatesOrderOnly() {
        RefundResultEventV2 event = createEvent(1L, "FAILED");
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setOrderId(1L);
        refund.setPaymentId(10L);
        refund.setVersion(1L);
        refund.setAmount(new java.math.BigDecimal("100.00"));
        refund.setCurrency("EUR");
        Payment payment = new Payment(); payment.setId(10L); payment.setVersion(1L); payment.setExternalPaymentId("pm_123");

        when(inboxService.isAlreadyProcessed("main-server", "evt-1")).thenReturn(false);
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(orderRepository.updateOrderStatusAndVersion(1L, OrderStatus.REFUND_PENDING, OrderStatus.REFUND_FAILED, 1L)).thenReturn(1);
        when(paymentRepository.updateStatusAndVersion(10L, PaymentStatus.REFUND_PENDING, PaymentStatus.REFUND_FAILED, 1L)).thenReturn(1);

        consumer.onRefundResult(event);

        assertEquals(Refund.Status.FAILED, refund.getStatus());
        verify(refundRepository).save(refund);
        verify(paymentRepository).findById(10L);
        verify(inboxService).markProcessed("main-server", "evt-1", "RefundResultEventV2", null, 1L);
    }

    @Test
    void onRefundResult_AlreadyProcessed_ReturnsEarly() {
        RefundResultEventV2 event = createEvent(1L, "SUCCEEDED");

        when(inboxService.isAlreadyProcessed("main-server", "evt-1")).thenReturn(true);

        consumer.onRefundResult(event);

        verify(refundRepository, never()).findById(any());
        verify(orderRepository, never()).updateOrderStatusAndVersion(any(), any(), any(), any());
        verify(inboxService, never()).markProcessed(any(), any(), any(), any(), any());
    }

    @Test
    void onRefundResult_RefundNotFound_ThrowsEntityNotFoundException() {
        RefundResultEventV2 event = createEvent(999L, "SUCCEEDED");

        when(inboxService.isAlreadyProcessed("main-server", "evt-1")).thenReturn(false);
        when(refundRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> consumer.onRefundResult(event));
    }

    @Test
    void onRefundResult_Succeeded_OrderVersionConflict_Throws() {
        RefundResultEventV2 event = createEvent(1L, "SUCCEEDED");
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setOrderId(1L);
        refund.setPaymentId(10L);
        refund.setVersion(1L);
        refund.setAmount(new java.math.BigDecimal("100.00")); refund.setCurrency("EUR");
        Payment payment = new Payment(); payment.setId(10L); payment.setVersion(1L); payment.setExternalPaymentId("pm_123");

        when(inboxService.isAlreadyProcessed("main-server", "evt-1")).thenReturn(false);
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(orderRepository.updateOrderStatusAndVersion(1L, OrderStatus.REFUND_PENDING, OrderStatus.REFUNDED, 1L)).thenReturn(0);

        assertThrows(OptimisticLockException.class, () -> consumer.onRefundResult(event));
    }

    @Test
    void onRefundResult_Failed_OrderVersionConflict_Throws() {
        RefundResultEventV2 event = createEvent(1L, "FAILED");
        Refund refund = new Refund();
        refund.setId(1L);
        refund.setOrderId(1L);
        refund.setPaymentId(10L);
        refund.setVersion(1L);
        refund.setAmount(new java.math.BigDecimal("100.00")); refund.setCurrency("EUR");
        Payment payment = new Payment(); payment.setId(10L); payment.setVersion(1L); payment.setExternalPaymentId("pm_123");

        when(inboxService.isAlreadyProcessed("main-server", "evt-1")).thenReturn(false);
        when(refundRepository.findById(1L)).thenReturn(Optional.of(refund));
        when(paymentRepository.findById(10L)).thenReturn(Optional.of(payment));
        when(orderRepository.updateOrderStatusAndVersion(1L, OrderStatus.REFUND_PENDING, OrderStatus.REFUND_FAILED, 1L)).thenReturn(0);

        assertThrows(OptimisticLockException.class, () -> consumer.onRefundResult(event));
    }
}
