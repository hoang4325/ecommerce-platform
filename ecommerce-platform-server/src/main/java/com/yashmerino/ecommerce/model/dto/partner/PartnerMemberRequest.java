package com.yashmerino.ecommerce.model.dto.partner;

import com.yashmerino.ecommerce.model.partner.PartnerMemberRole;
import jakarta.validation.constraints.NotNull;

public record PartnerMemberRequest(@NotNull Long userId, @NotNull PartnerMemberRole role) {}
