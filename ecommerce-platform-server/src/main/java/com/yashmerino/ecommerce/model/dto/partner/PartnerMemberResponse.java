package com.yashmerino.ecommerce.model.dto.partner;

import com.yashmerino.ecommerce.model.partner.PartnerMember;
import com.yashmerino.ecommerce.model.partner.PartnerMemberRole;
import com.yashmerino.ecommerce.model.partner.PartnerMemberStatus;

import java.time.LocalDateTime;

public record PartnerMemberResponse(
        Long id,
        Long partnerId,
        Long userId,
        String username,
        String email,
        PartnerMemberRole role,
        PartnerMemberStatus status,
        LocalDateTime joinedAt
) {
    public static PartnerMemberResponse from(PartnerMember member) {
        var user = member.getUser();
        var joinedAt = member.getJoinedAt() != null ? member.getJoinedAt() : member.getCreatedAt();
        return new PartnerMemberResponse(
                member.getId(),
                member.getPartner().getId(),
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                member.getRole(),
                member.getStatus(),
                joinedAt
        );
    }
}
