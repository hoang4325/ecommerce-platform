package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.kafka.events.RefundRequestedEventV2;
import com.yashmerino.ecommerce.model.Order;
import com.yashmerino.ecommerce.model.Payment;
import com.yashmerino.ecommerce.model.domain.Refund;
import com.yashmerino.ecommerce.model.dto.RefundRequestDTO;
import com.yashmerino.ecommerce.repositories.OrderRepository;
import com.yashmerino.ecommerce.repositories.PaymentRepository;
import com.yashmerino.ecommerce.repositories.RefundRepository;
import com.yashmerino.ecommerce.utils.OrderStatus;
import com.yashmerino.ecommerce.utils.PaymentStatus;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RefundService {

    private final RefundRepository refundRepository;
    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final OutboxService outboxService;

    @Transactional
    public Refund requestRefund(Long orderId, Long userId, UUID requestKey, RefundRequestDTO dto) {
        Order order = orderRepository.findById(orderId)
            .orElseThrow(() -> new EntityNotFoundException("order_not_found"));

        if (order.getStatus() != OrderStatus.PAID) {
            throw new IllegalStateException("order_not_in_paid_status");
        }

        if (!order.getUser().getId().equals(userId)) {
            throw new AccessDeniedException("order_not_owned");
        }

        Payment payment = paymentRepository.findByOrderIdAndStatus(orderId, PaymentStatus.SUCCEEDED)
            .orElseThrow(() -> new EntityNotFoundException("no_successful_payment_found"));

        String idempotencyKey = "refund-request:" + requestKey;

        var previous = refundRepository.findByRequestIdempotencyKey(idempotencyKey);
        if (previous.isPresent()) return previous.get();

        Refund refund = new Refund(orderId, payment.getId(), order.getTotalAmount(),
            order.getCurrency(), dto.reason(), userId, idempotencyKey);
        refund = refundRepository.save(refund);

        int orderUpdated = orderRepository.updateOrderStatusAndVersion(
            orderId, OrderStatus.PAID, OrderStatus.REFUND_PENDING, order.getVersion());
        if (orderUpdated == 0) throw new OptimisticLockException("Order version conflict");

        int paymentUpdated = paymentRepository.updateStatusAndVersion(
            payment.getId(), PaymentStatus.SUCCEEDED, PaymentStatus.REFUND_PENDING, payment.getVersion());
        if (paymentUpdated == 0) throw new OptimisticLockException("Payment version conflict");

        String eventId = UUID.randomUUID().toString();
        String now = LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME);

        RefundRequestedEventV2 event = new RefundRequestedEventV2(
            eventId, "RefundRequestedEventV2", 2, now, null,
            refund.getId(), "main-server", idempotencyKey,
            refund.getId(), orderId, payment.getId(),
            payment.getExternalPaymentId(),
            refund.getAmount().toString(), refund.getCurrency(),
            dto.reason(), userId.toString(), idempotencyKey
        );

        outboxService.saveOutboxEvent(eventId, "refund", refund.getId(),
            "RefundRequestedEventV2", "payment.refund.requested.v2",
            payment.getId().toString(), event, idempotencyKey);

        return refund;
    }
}
