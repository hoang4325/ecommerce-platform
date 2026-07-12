package com.yashmerino.ecommerce.model.dto.partner;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record PartnerProfileUpdateRequest(
        @NotBlank @Size(max = 255) String name,
        @NotBlank @Size(max = 255) String businessName,
        @NotBlank @Email @Size(max = 255) String email,
        @Pattern(regexp = "[+0-9() .-]{0,50}") String phone,
        @Size(max = 500) String address) {
}

