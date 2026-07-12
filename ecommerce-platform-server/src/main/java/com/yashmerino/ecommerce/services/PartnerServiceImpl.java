package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.Role;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.partner.PartnerApplicationRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDecisionRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerProfileUpdateRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerResponse;
import com.yashmerino.ecommerce.model.partner.*;
import com.yashmerino.ecommerce.repositories.PartnerAuditEventRepository;
import com.yashmerino.ecommerce.repositories.PartnerMemberRepository;
import com.yashmerino.ecommerce.repositories.PartnerRepository;
import com.yashmerino.ecommerce.repositories.RoleRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import com.yashmerino.ecommerce.services.interfaces.PartnerService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class PartnerServiceImpl implements PartnerService {

    private final PartnerRepository partnerRepository;
    private final PartnerMemberRepository partnerMemberRepository;
    private final PartnerAuditEventRepository auditEventRepository;
    private final PartnerAuthorizationService authz;
    private final RoleRepository roleRepository;

    @Override
    @Transactional
    public PartnerResponse createApplication(PartnerApplicationRequest request) {
        User user = authz.getCurrentUser();

        if (partnerRepository.existsByApplicantIdAndStatusNot(user.getId(), PartnerStatus.TERMINATED)) {
            throw new ConflictException("already_has_active_application");
        }
        if (partnerRepository.existsByTaxCode(request.taxCode())) {
            throw new ConflictException("tax_code_already_registered");
        }

        Partner partner = new Partner();
        partner.setCode("APP-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        partner.setName(request.name());
        partner.setBusinessName(request.businessName());
        partner.setTaxCode(request.taxCode());
        partner.setEmail(request.email());
        partner.setPhone(request.phone() != null ? request.phone() : "");
        partner.setAddress(request.address() != null ? request.address() : "");
        partner.setStatus(PartnerStatus.DRAFT);
        partner.setApplicant(user);
        partner = partnerRepository.save(partner);

        auditEventRepository.save(createAuditEvent(partner, "APPLICATION_CREATED", null, PartnerStatus.DRAFT.name(), user.getId(), "Application created"));

        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerResponse getMyProfile() {
        User user = authz.getCurrentUser();
        Partner partner = partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("no_partner_application"));
        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional
    public PartnerResponse updateMyProfile(PartnerProfileUpdateRequest request) {
        User user = authz.getCurrentUser();
        Partner partner = partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("no_partner_application"));

        if (partner.getStatus() != PartnerStatus.DRAFT && partner.getStatus() != PartnerStatus.CHANGES_REQUESTED) {
            throw new InvalidInputException("cannot_update_in_current_status");
        }

        partner.setName(request.name());
        partner.setBusinessName(request.businessName());
        partner.setEmail(request.email());
        partner.setPhone(request.phone() != null ? request.phone() : "");
        partner.setAddress(request.address() != null ? request.address() : "");
        partner = partnerRepository.save(partner);

        auditEventRepository.save(createAuditEvent(partner, "PROFILE_UPDATED", null, partner.getStatus().name(), user.getId(), "Profile updated"));

        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerResponse getMyStatus() {
        User user = authz.getCurrentUser();
        Partner partner = partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("no_partner_application"));
        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional
    public PartnerResponse submitApplication() {
        User user = authz.getCurrentUser();
        Partner partner = partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId())
                .orElseThrow(() -> new EntityNotFoundException("no_partner_application"));

        if (partner.getStatus() != PartnerStatus.DRAFT && partner.getStatus() != PartnerStatus.CHANGES_REQUESTED) {
            throw new InvalidInputException("cannot_submit_in_current_status");
        }

        partner.setStatus(PartnerStatus.PENDING_REVIEW);
        partner = partnerRepository.save(partner);

        auditEventRepository.save(createAuditEvent(partner, "APPLICATION_SUBMITTED", null, PartnerStatus.PENDING_REVIEW.name(), user.getId(), "Submitted for review"));

        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional(readOnly = true)
    public PartnerResponse getPartner(Long partnerId) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_not_found"));
        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartnerResponse> getAllPartners(Pageable pageable) {
        return partnerRepository.findAll(pageable).map(PartnerResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<PartnerResponse> getPartnersByStatus(String status, Pageable pageable) {
        PartnerStatus partnerStatus;
        try {
            partnerStatus = PartnerStatus.valueOf(status.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidInputException("invalid_status");
        }
        return partnerRepository.findByStatus(partnerStatus, pageable).map(PartnerResponse::from);
    }

    @Override
    @Transactional
    public PartnerResponse approvePartner(Long partnerId, PartnerDecisionRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_not_found"));

        if (partner.getStatus() != PartnerStatus.PENDING_REVIEW) {
            throw new InvalidInputException("cannot_approve_in_current_status");
        }

        partner.setStatus(PartnerStatus.APPROVED);
        partner.setApprovedAt(LocalDateTime.now());
        partner.setRejectedAt(null);
        partner.setRejectionReason(null);
        User admin = authz.getCurrentUser();
        partner.setApprovedBy(admin);
        partner = partnerRepository.save(partner);

        PartnerMember member = new PartnerMember();
        member.setPartner(partner);
        member.setUser(partner.getApplicant());
        member.setRole(PartnerMemberRole.OWNER);
        member.setStatus(PartnerMemberStatus.ACTIVE);
        member.setJoinedAt(LocalDateTime.now());
        partnerMemberRepository.save(member);

        auditEventRepository.save(createAuditEvent(partner, "APPROVED", PartnerStatus.PENDING_REVIEW.name(), PartnerStatus.APPROVED.name(), admin.getId(), request.reason()));

        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional
    public PartnerResponse rejectPartner(Long partnerId, PartnerDecisionRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_not_found"));

        if (partner.getStatus() != PartnerStatus.PENDING_REVIEW && partner.getStatus() != PartnerStatus.CHANGES_REQUESTED) {
            throw new InvalidInputException("cannot_reject_in_current_status");
        }

        partner.setStatus(PartnerStatus.REJECTED);
        partner.setRejectedAt(LocalDateTime.now());
        partner.setRejectionReason(request.reason());
        User admin = authz.getCurrentUser();
        partner.setApprovedBy(null);
        partner.setApprovedAt(null);
        partner = partnerRepository.save(partner);

        auditEventRepository.save(createAuditEvent(partner, "REJECTED", null, PartnerStatus.REJECTED.name(), admin.getId(), request.reason()));

        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional
    public PartnerResponse suspendPartner(Long partnerId, PartnerDecisionRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_not_found"));

        if (partner.getStatus() != PartnerStatus.APPROVED) {
            throw new InvalidInputException("cannot_suspend_in_current_status");
        }

        partner.setStatus(PartnerStatus.SUSPENDED);
        partner.setSuspendedAt(LocalDateTime.now());
        partner.setSuspensionReason(request.reason());
        User admin = authz.getCurrentUser();
        partner = partnerRepository.save(partner);

        auditEventRepository.save(createAuditEvent(partner, "SUSPENDED", PartnerStatus.APPROVED.name(), PartnerStatus.SUSPENDED.name(), admin.getId(), request.reason()));

        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional
    public PartnerResponse restorePartner(Long partnerId, PartnerDecisionRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_not_found"));

        if (partner.getStatus() != PartnerStatus.SUSPENDED) {
            throw new InvalidInputException("cannot_restore_in_current_status");
        }

        partner.setStatus(PartnerStatus.APPROVED);
        partner.setSuspendedAt(null);
        partner.setSuspensionReason(null);
        User admin = authz.getCurrentUser();
        partner = partnerRepository.save(partner);

        auditEventRepository.save(createAuditEvent(partner, "RESTORED", PartnerStatus.SUSPENDED.name(), PartnerStatus.APPROVED.name(), admin.getId(), request.reason()));

        return PartnerResponse.from(partner);
    }

    @Override
    @Transactional
    public PartnerResponse terminatePartner(Long partnerId, PartnerDecisionRequest request) {
        Partner partner = partnerRepository.findById(partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_not_found"));

        if (partner.getStatus() == PartnerStatus.TERMINATED) {
            throw new InvalidInputException("already_terminated");
        }

        partner.setStatus(PartnerStatus.TERMINATED);
        User admin = authz.getCurrentUser();
        partner = partnerRepository.save(partner);

        auditEventRepository.save(createAuditEvent(partner, "TERMINATED", null, PartnerStatus.TERMINATED.name(), admin.getId(), request.reason()));

        return PartnerResponse.from(partner);
    }

    private PartnerAuditEvent createAuditEvent(Partner partner, String action, String fromState, String toState, Long actorId, String reason) {
        PartnerAuditEvent event = new PartnerAuditEvent();
        event.setPartner(partner);
        event.setAction(action);
        event.setFromState(fromState);
        event.setToState(toState);
        event.setActor(authz.getCurrentUser());
        event.setReason(reason);
        return event;
    }
}
