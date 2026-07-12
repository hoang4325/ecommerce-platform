package com.yashmerino.ecommerce.model.dto.partner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerDecisionRequest(@NotBlank @Size(max = 1000) String reason) {
}

