package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.model.Order;
import com.yashmerino.ecommerce.model.Payment;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.domain.Refund;
import com.yashmerino.ecommerce.model.dto.RefundRequestDTO;
import com.yashmerino.ecommerce.repositories.OrderRepository;
import com.yashmerino.ecommerce.repositories.PaymentRepository;
import com.yashmerino.ecommerce.repositories.RefundRepository;
import com.yashmerino.ecommerce.utils.OrderStatus;
import com.yashmerino.ecommerce.utils.PaymentStatus;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.math.BigDecimal;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundServiceTest {

    @Mock
    private RefundRepository refundRepository;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private RefundService refundService;

    private Order createPaidOrder(Long id, Long userId, Long version) {
        Order order = new Order();
        order.setId(id);
        order.setStatus(OrderStatus.PAID);
        order.setTotalAmount(new BigDecimal("100.00"));
        order.setCurrency("EUR");
        order.setVersion(version);

        User user = new User();
        user.setId(userId);
        order.setUser(user);

        return order;
    }

    @Test
    void requestRefund_WithValidPaidOrder_CreatesRefundAndEmitsEvent() {
        Long orderId = 1L;
        Long userId = 10L;
        RefundRequestDTO dto = new RefundRequestDTO("item not as described");

        Order order = createPaidOrder(orderId, userId, 1L);

        Payment payment = new Payment();
        payment.setId(100L);
        payment.setVersion(1L);
        payment.setExternalPaymentId("pi_123");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED))
            .thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(999L);
            return r;
        });
        when(orderRepository.updateOrderStatusAndVersion(orderId, OrderStatus.PAID, OrderStatus.REFUND_PENDING, 1L))
            .thenReturn(1);
        when(paymentRepository.updateStatusAndVersion(100L, PaymentStatus.SUCCEEDED, PaymentStatus.REFUND_PENDING, 1L))
            .thenReturn(1);

        Refund result = refundService.requestRefund(orderId, userId, java.util.UUID.fromString("00000000-0000-0000-0000-000000000001"), dto);

        assertNotNull(result);
        assertEquals(999L, result.getId());

        ArgumentCaptor<Refund> refundCaptor = ArgumentCaptor.forClass(Refund.class);
        verify(refundRepository).save(refundCaptor.capture());
        Refund saved = refundCaptor.getValue();
        assertEquals(orderId, saved.getOrderId());
        assertEquals(100L, saved.getPaymentId());
        assertEquals(new BigDecimal("100.00"), saved.getAmount());
        assertEquals("EUR", saved.getCurrency());
        assertEquals("item not as described", saved.getReason());
        assertEquals(userId, saved.getRequestedBy());
        assertEquals("refund-request:00000000-0000-0000-0000-000000000001", saved.getRequestIdempotencyKey());

        verify(outboxService).saveOutboxEvent(
            anyString(), eq("refund"), eq(999L),
            eq("RefundRequestedEventV2"), eq("payment.refund.requested.v2"),
            eq("100"), any(), anyString()
        );
    }

    @Test
    void requestRefund_WithNonPaidOrder_ThrowsIllegalStateException() {
        Long orderId = 1L;
        Long userId = 10L;
        RefundRequestDTO dto = new RefundRequestDTO("reason");

        Order order = new Order();
        order.setId(orderId);
        order.setStatus(OrderStatus.CREATED);
        User user = new User();
        user.setId(userId);
        order.setUser(user);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(IllegalStateException.class,
            () -> refundService.requestRefund(orderId, userId, java.util.UUID.randomUUID(), dto));
        verify(refundRepository, never()).save(any());
        verify(outboxService, never()).saveOutboxEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void requestRefund_WithDuplicateIdempotencyKey_ReturnsPreviousResult() {
        Long orderId = 1L;
        Long userId = 10L;
        RefundRequestDTO dto = new RefundRequestDTO("reason");

        Order order = createPaidOrder(orderId, userId, 1L);

        Payment payment = new Payment();
        payment.setId(100L);
        payment.setVersion(1L);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED))
            .thenReturn(Optional.of(payment));
        Refund previous = new Refund(); previous.setId(77L);
        when(refundRepository.findByRequestIdempotencyKey(anyString())).thenReturn(Optional.of(previous));

        Refund result = refundService.requestRefund(orderId, userId, java.util.UUID.randomUUID(), dto);
        assertSame(previous, result);
        verify(refundRepository, never()).save(any());
        verify(outboxService, never()).saveOutboxEvent(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    void requestRefund_WhenOrderNotFound_ThrowsEntityNotFoundException() {
        Long orderId = 999L;
        Long userId = 10L;
        RefundRequestDTO dto = new RefundRequestDTO("reason");

        when(orderRepository.findById(orderId)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
            () -> refundService.requestRefund(orderId, userId, java.util.UUID.randomUUID(), dto));
    }

    @Test
    void requestRefund_WhenNotOwner_ThrowsAccessDeniedException() {
        Long orderId = 1L;
        Long userId = 10L;
        RefundRequestDTO dto = new RefundRequestDTO("reason");

        Order order = createPaidOrder(orderId, 20L, 1L);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));

        assertThrows(AccessDeniedException.class,
            () -> refundService.requestRefund(orderId, userId, java.util.UUID.randomUUID(), dto));
    }

    @Test
    void requestRefund_WhenNoSuccessfulPayment_ThrowsEntityNotFoundException() {
        Long orderId = 1L;
        Long userId = 10L;
        RefundRequestDTO dto = new RefundRequestDTO("reason");

        Order order = createPaidOrder(orderId, userId, 1L);

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED))
            .thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
            () -> refundService.requestRefund(orderId, userId, java.util.UUID.randomUUID(), dto));
    }

    @Test
    void requestRefund_WhenOrderUpdateFailsDueToVersionConflict_ThrowsOptimisticLockException() {
        Long orderId = 1L;
        Long userId = 10L;
        RefundRequestDTO dto = new RefundRequestDTO("reason");

        Order order = createPaidOrder(orderId, userId, 1L);

        Payment payment = new Payment();
        payment.setId(100L);
        payment.setVersion(1L);
        payment.setExternalPaymentId("pi_123");

        when(orderRepository.findById(orderId)).thenReturn(Optional.of(order));
        when(paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED))
            .thenReturn(Optional.of(payment));
        when(refundRepository.save(any(Refund.class))).thenAnswer(invocation -> {
            Refund r = invocation.getArgument(0);
            r.setId(999L);
            return r;
        });
        when(orderRepository.updateOrderStatusAndVersion(orderId, OrderStatus.PAID, OrderStatus.REFUND_PENDING, 1L))
            .thenReturn(0);

        assertThrows(jakarta.persistence.OptimisticLockException.class,
            () -> refundService.requestRefund(orderId, userId, java.util.UUID.randomUUID(), dto));
    }
}
