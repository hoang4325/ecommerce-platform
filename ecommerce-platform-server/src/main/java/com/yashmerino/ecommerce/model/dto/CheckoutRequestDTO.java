package com.yashmerino.ecommerce.model.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CheckoutRequestDTO(
        @Min(0) @Max(1_000_000) Integer requestedPoints,
        @Size(max = 50) @Pattern(regexp = "[A-Za-z0-9_-]*") String couponCode,
        @Pattern(regexp = "[A-Z]{3}") String currency) {
    public CheckoutRequestDTO {
        requestedPoints = requestedPoints == null ? 0 : requestedPoints;
    }
}
