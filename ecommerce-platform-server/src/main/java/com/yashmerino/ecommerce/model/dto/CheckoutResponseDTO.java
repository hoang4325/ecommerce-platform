package com.yashmerino.ecommerce.model.dto;

import com.yashmerino.ecommerce.utils.PaymentStatus;
import java.math.BigDecimal;
import java.time.Instant;

public record CheckoutResponseDTO(Long orderId, Long paymentId, BigDecimal amount, String currency,
                                  PaymentStatus paymentStatus, Instant reservationExpiresAt) {}
