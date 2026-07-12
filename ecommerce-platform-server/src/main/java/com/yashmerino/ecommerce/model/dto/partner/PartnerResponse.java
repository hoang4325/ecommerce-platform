package com.yashmerino.ecommerce.model.dto.partner;

import com.yashmerino.ecommerce.model.partner.Partner;
import com.yashmerino.ecommerce.model.partner.PartnerStatus;

import java.time.LocalDateTime;

public record PartnerResponse(
        Long id,
        String code,
        String name,
        String businessName,
        String taxCode,
        String email,
        String phone,
        String address,
        PartnerStatus status,
        LocalDateTime approvedAt,
        LocalDateTime rejectedAt,
        String rejectionReason,
        LocalDateTime suspendedAt,
        String suspensionReason,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {
    public static PartnerResponse from(Partner partner) {
        return new PartnerResponse(
                partner.getId(), partner.getCode(), partner.getName(), partner.getBusinessName(),
                partner.getTaxCode(), partner.getEmail(), partner.getPhone(), partner.getAddress(),
                partner.getStatus(), partner.getApprovedAt(), partner.getRejectedAt(),
                partner.getRejectionReason(), partner.getSuspendedAt(), partner.getSuspensionReason(),
                partner.getVersion(), partner.getCreatedAt(), partner.getUpdatedAt());
    }
}

