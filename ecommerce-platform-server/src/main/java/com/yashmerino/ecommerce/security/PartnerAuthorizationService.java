package com.yashmerino.ecommerce.security;

import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.partner.Partner;
import com.yashmerino.ecommerce.model.partner.PartnerMember;
import com.yashmerino.ecommerce.model.partner.PartnerMemberRole;
import com.yashmerino.ecommerce.model.partner.PartnerMemberStatus;
import com.yashmerino.ecommerce.model.partner.PartnerStatus;
import com.yashmerino.ecommerce.repositories.PartnerMemberRepository;
import com.yashmerino.ecommerce.repositories.PartnerRepository;
import com.yashmerino.ecommerce.repositories.UserRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PartnerAuthorizationService {

    private final PartnerMemberRepository partnerMemberRepository;
    private final PartnerRepository partnerRepository;
    private final UserRepository userRepository;

    public User getCurrentUser() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            throw new AccessDeniedException("not_authenticated");
        }
        return userRepository.findByUsername(auth.getName())
                .orElseThrow(() -> new EntityNotFoundException("user_not_found"));
    }

    public PartnerMember getActiveMember(Long partnerId, Long userId) {
        PartnerMember member = partnerMemberRepository.findByPartnerIdAndUserId(partnerId, userId)
                .orElseThrow(() -> new AccessDeniedException("access_denied"));
        if (member.getStatus() != PartnerMemberStatus.ACTIVE) {
            throw new AccessDeniedException("membership_not_active");
        }
        return member;
    }

    public PartnerMember getActiveMember(Long partnerId) {
        User user = getCurrentUser();
        return getActiveMember(partnerId, user.getId());
    }

    public void requireRole(Long partnerId, PartnerMemberRole requiredRole) {
        PartnerMember member = getActiveMember(partnerId);
        if (member.getRole().ordinal() > requiredRole.ordinal()) {
            throw new AccessDeniedException("insufficient_permissions");
        }
    }

    public void requireAnyRole(Long partnerId, Set<PartnerMemberRole> allowedRoles) {
        PartnerMember member = getActiveMember(partnerId);
        if (allowedRoles.stream().noneMatch(r -> r == member.getRole())) {
            throw new AccessDeniedException("insufficient_permissions");
        }
    }

    public void requirePartnerActive(Long partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_not_found"));
        if (partner.getStatus() != PartnerStatus.APPROVED) {
            throw new AccessDeniedException("partner_not_active");
        }
    }

    public void requireAllowsCommand(Long partnerId, PartnerMemberRole requiredRole) {
        requirePartnerActive(partnerId);
        requireRole(partnerId, requiredRole);
    }

    public void requireAllowsCommand(Long partnerId, Set<PartnerMemberRole> allowedRoles) {
        requirePartnerActive(partnerId);
        requireAnyRole(partnerId, allowedRoles);
    }

    public List<PartnerMember> getUserActiveMemberships(User user) {
        return partnerMemberRepository.findByUserIdAndStatus(user.getId(), PartnerMemberStatus.ACTIVE);
    }

    public void verifyResourceOwnership(Long partnerId, Long resourcePartnerId, String resourceName) {
        if (!partnerId.equals(resourcePartnerId)) {
            throw new AccessDeniedException(resourceName + "_access_denied");
        }
    }
}
