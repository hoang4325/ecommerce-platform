package com.yashmerino.ecommerce.model.dto.commission;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record UpdateCommissionRuleRequest(
        String name,
        @DecimalMin("0.0000") @DecimalMax("100.0000") BigDecimal rate,
        @DecimalMin("0.00") BigDecimal fixedFee,
        String currency,
        Integer priority,
        LocalDateTime validFrom,
        LocalDateTime validTo
) {
}
