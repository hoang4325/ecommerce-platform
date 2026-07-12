package com.yashmerino.ecommerce.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record PaymentInitiationRequestDTO(
        @NotBlank @Pattern(regexp = "pm_[A-Za-z0-9_]{3,252}") String paymentMethodId) {}
