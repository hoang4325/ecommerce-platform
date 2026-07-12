package com.yashmerino.ecommerce.model.dto.partner;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PartnerBankAccountRequest(
        @NotBlank @Size(max = 255) String bankName,
        @NotBlank @Size(max = 255) String accountName,
        @NotBlank @Size(min = 4, max = 64) String accountNumber
) {}
