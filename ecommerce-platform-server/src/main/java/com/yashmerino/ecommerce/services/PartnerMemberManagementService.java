package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.partner.PartnerMemberRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerMemberResponse;
import com.yashmerino.ecommerce.model.partner.*;
import com.yashmerino.ecommerce.repositories.PartnerMemberRepository;
import com.yashmerino.ecommerce.repositories.UserRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnerMemberManagementService {
    private final PartnerMemberRepository memberRepository;
    private final UserRepository userRepository;
    private final PartnerAuthorizationService authz;

    @Transactional(readOnly = true)
    public List<PartnerMemberResponse> list(Long partnerId) {
        authz.requireMemberManagement(partnerId);
        return memberRepository.findByPartnerId(partnerId).stream().map(PartnerMemberResponse::from).toList();
    }

    @Transactional
    public PartnerMemberResponse invite(Long partnerId, PartnerMemberRequest request) {
        authz.requireMemberManagement(partnerId);
        if (request.role() == PartnerMemberRole.OWNER) throw new ConflictException("owner_invite_not_allowed");
        User user = userRepository.findById(request.userId()).orElseThrow(() -> new EntityNotFoundException("user_not_found"));
        Partner partner = new Partner();
        partner.setId(partnerId);
        PartnerMember member = memberRepository.findByPartnerIdAndUserId(partnerId, request.userId()).orElseGet(PartnerMember::new);
        member.setPartner(partner);
        member.setUser(user);
        member.setRole(request.role());
        member.setStatus(PartnerMemberStatus.INVITED);
        return PartnerMemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public PartnerMemberResponse activate(Long partnerId, Long memberId) {
        authz.requireMemberManagement(partnerId);
        PartnerMember member = find(partnerId, memberId);
        member.setStatus(PartnerMemberStatus.ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        return PartnerMemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public PartnerMemberResponse suspend(Long partnerId, Long memberId) {
        authz.requireMemberManagement(partnerId);
        PartnerMember member = find(partnerId, memberId);
        if (member.getRole() == PartnerMemberRole.OWNER) throw new ConflictException("owner_suspend_not_allowed");
        member.setStatus(PartnerMemberStatus.SUSPENDED);
        return PartnerMemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public PartnerMemberResponse restore(Long partnerId, Long memberId) {
        authz.requireMemberManagement(partnerId);
        PartnerMember member = find(partnerId, memberId);
        member.setStatus(PartnerMemberStatus.ACTIVE);
        return PartnerMemberResponse.from(memberRepository.save(member));
    }

    @Transactional
    public PartnerMemberResponse transferOwnership(Long partnerId, Long memberId) {
        authz.requireMemberManagement(partnerId);
        PartnerMember current = authz.getActiveMember(partnerId);
        if (current.getRole() != PartnerMemberRole.OWNER) throw new ConflictException("owner_required");
        PartnerMember nextOwner = find(partnerId, memberId);
        nextOwner.setRole(PartnerMemberRole.OWNER);
        nextOwner.setStatus(PartnerMemberStatus.ACTIVE);
        current.setRole(PartnerMemberRole.MANAGER);
        memberRepository.save(current);
        return PartnerMemberResponse.from(memberRepository.save(nextOwner));
    }

    private PartnerMember find(Long partnerId, Long memberId) {
        return memberRepository.findByIdAndPartnerId(memberId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_member_not_found"));
    }
}
