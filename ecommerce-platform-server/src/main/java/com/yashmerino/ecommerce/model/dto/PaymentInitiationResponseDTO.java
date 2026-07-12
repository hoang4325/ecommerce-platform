package com.yashmerino.ecommerce.model.dto;

import com.yashmerino.ecommerce.utils.PaymentStatus;
import java.math.BigDecimal;

public record PaymentInitiationResponseDTO(Long orderId, Long paymentId, BigDecimal amount,
                                           String currency, PaymentStatus paymentStatus) {}
