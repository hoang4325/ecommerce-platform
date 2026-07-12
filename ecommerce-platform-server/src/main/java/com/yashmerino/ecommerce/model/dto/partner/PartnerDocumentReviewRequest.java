package com.yashmerino.ecommerce.model.dto.partner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerDocumentReviewRequest(@NotBlank String status, @Size(max = 1000) String rejectionReason) {}
