package com.yashmerino.ecommerce.model.dto.settlement;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record SettlementAdjustmentRequest(
        @NotNull BigDecimal amount,
        @NotBlank String reason,
        String idempotencyKey) {
}
