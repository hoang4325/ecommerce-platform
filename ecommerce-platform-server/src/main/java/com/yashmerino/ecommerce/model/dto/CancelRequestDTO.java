package com.yashmerino.ecommerce.model.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CancelRequestDTO(@NotBlank @Size(max = 500) String reason) {}
