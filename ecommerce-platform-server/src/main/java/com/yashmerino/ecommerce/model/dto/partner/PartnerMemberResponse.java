package com.yashmerino.ecommerce.model.dto.partner;

import com.yashmerino.ecommerce.model.partner.PartnerMember;
import com.yashmerino.ecommerce.model.partner.PartnerMemberRole;
import com.yashmerino.ecommerce.model.partner.PartnerMemberStatus;

public record PartnerMemberResponse(Long id, Long partnerId, Long userId, PartnerMemberRole role, PartnerMemberStatus status) {
    public static PartnerMemberResponse from(PartnerMember member) {
        return new PartnerMemberResponse(member.getId(), member.getPartner().getId(), member.getUser().getId(), member.getRole(), member.getStatus());
    }
}
