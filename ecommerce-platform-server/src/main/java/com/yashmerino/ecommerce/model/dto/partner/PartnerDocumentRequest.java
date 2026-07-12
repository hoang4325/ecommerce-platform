package com.yashmerino.ecommerce.model.dto.partner;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PartnerDocumentRequest(
        @NotBlank @Size(max = 50) String documentType,
        @NotBlank @Size(max = 500) String objectKey,
        @NotBlank @Size(max = 255) String originalFileName,
        @NotBlank @Size(max = 100) String contentType,
        @NotNull @Min(1) Long fileSize,
        @NotBlank @Size(max = 128) String checksum
) {}
