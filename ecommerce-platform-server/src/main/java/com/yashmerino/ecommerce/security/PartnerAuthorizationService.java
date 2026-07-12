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

    private static final Set<PartnerMemberRole> OFFER_WRITE = Set.of(
            PartnerMemberRole.OWNER, PartnerMemberRole.MANAGER, PartnerMemberRole.PRODUCT_STAFF);
    private static final Set<PartnerMemberRole> INVENTORY_WRITE = Set.of(
            PartnerMemberRole.OWNER, PartnerMemberRole.MANAGER, PartnerMemberRole.PRODUCT_STAFF);
    private static final Set<PartnerMemberRole> ORDER_FULFILLMENT = Set.of(
            PartnerMemberRole.OWNER, PartnerMemberRole.MANAGER, PartnerMemberRole.ORDER_STAFF);
    private static final Set<PartnerMemberRole> SETTLEMENT_READ = Set.of(
            PartnerMemberRole.OWNER, PartnerMemberRole.MANAGER, PartnerMemberRole.FINANCE_STAFF);
    private static final Set<PartnerMemberRole> BANK_ACCOUNT_MANAGEMENT = Set.of(
            PartnerMemberRole.OWNER, PartnerMemberRole.MANAGER, PartnerMemberRole.FINANCE_STAFF);
    private static final Set<PartnerMemberRole> MEMBER_MANAGEMENT = Set.of(
            PartnerMemberRole.OWNER, PartnerMemberRole.MANAGER);
    private static final Set<PartnerMemberRole> PROFILE_MANAGEMENT = Set.of(
            PartnerMemberRole.OWNER);

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

    public void requirePartnerActive(Long partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_not_found"));
        if (partner.getStatus() != PartnerStatus.APPROVED) {
            throw new AccessDeniedException("partner_not_active");
        }
    }

    public void requireMembership(Long partnerId) {
        getActiveMember(partnerId);
    }

    public void requireOfferWrite(Long partnerId) {
        requirePartnerActive(partnerId);
        requireAnyRole(partnerId, OFFER_WRITE);
    }

    public void requireInventoryWrite(Long partnerId) {
        requirePartnerActive(partnerId);
        requireAnyRole(partnerId, INVENTORY_WRITE);
    }

    public void requireOrderFulfillment(Long partnerId) {
        requirePartnerActive(partnerId);
        requireAnyRole(partnerId, ORDER_FULFILLMENT);
    }

    public void requireSettlementRead(Long partnerId) {
        requirePartnerActive(partnerId);
        requireAnyRole(partnerId, SETTLEMENT_READ);
    }

    public void requireBankAccountManagement(Long partnerId) {
        requirePartnerActive(partnerId);
        requireAnyRole(partnerId, BANK_ACCOUNT_MANAGEMENT);
    }

    public void requireMemberManagement(Long partnerId) {
        requirePartnerActive(partnerId);
        requireAnyRole(partnerId, MEMBER_MANAGEMENT);
    }

    public void requireOfferRead(Long partnerId) {
        getActiveMember(partnerId);
        requirePartnerActive(partnerId);
    }

    public void requireOrderRead(Long partnerId) {
        getActiveMember(partnerId);
        requirePartnerActive(partnerId);
    }

    public void requireAllowsCommand(Long partnerId, Set<PartnerMemberRole> allowedRoles) {
        requirePartnerActive(partnerId);
        requireAnyRole(partnerId, allowedRoles);
    }

    private void requireAnyRole(Long partnerId, Set<PartnerMemberRole> allowedRoles) {
        PartnerMember member = getActiveMember(partnerId);
        if (allowedRoles.stream().noneMatch(r -> r == member.getRole())) {
            throw new AccessDeniedException("insufficient_permissions");
        }
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
