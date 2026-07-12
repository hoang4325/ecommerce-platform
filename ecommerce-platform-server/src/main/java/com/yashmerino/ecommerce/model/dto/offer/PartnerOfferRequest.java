package com.yashmerino.ecommerce.model.dto.offer;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

public record PartnerOfferRequest(
        @NotNull Long productId,
        @Size(max = 100) String partnerSku,
        @NotNull @DecimalMin("0.01") BigDecimal price,
        @Size(max = 3) String currency,
        @NotNull Integer onHandQuantity) {
}
