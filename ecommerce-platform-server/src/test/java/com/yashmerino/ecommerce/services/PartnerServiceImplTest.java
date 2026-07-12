package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.Role;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.partner.PartnerApplicationRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDecisionRequest;
import com.yashmerino.ecommerce.model.partner.*;
import com.yashmerino.ecommerce.repositories.PartnerAuditEventRepository;
import com.yashmerino.ecommerce.repositories.PartnerMemberRepository;
import com.yashmerino.ecommerce.repositories.PartnerRepository;
import com.yashmerino.ecommerce.repositories.RoleRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashSet;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerServiceImplTest {

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private PartnerMemberRepository partnerMemberRepository;

    @Mock
    private PartnerAuditEventRepository auditEventRepository;

    @Mock
    private PartnerAuthorizationService authz;

    @Mock
    private RoleRepository roleRepository;

    @InjectMocks
    private PartnerServiceImpl partnerService;

    private User user;
    private User admin;
    private Partner partner;
    private PartnerApplicationRequest appRequest;
    private PartnerDecisionRequest decisionRequest;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        user.setRoles(new HashSet<>());

        admin = new User();
        admin.setId(2L);

        partner = new Partner();
        partner.setId(10L);
        partner.setCode("APP-12345678");
        partner.setName("Test Partner");
        partner.setBusinessName("Test Business");
        partner.setTaxCode("TAX123");
        partner.setEmail("partner@test.com");
        partner.setPhone("123456789");
        partner.setAddress("123 Test St");
        partner.setStatus(PartnerStatus.DRAFT);
        partner.setApplicant(user);

        appRequest = new PartnerApplicationRequest(
                "Test Partner", "Test Business", "TAX123",
                "partner@test.com", "123456789", "123 Test St");

        decisionRequest = new PartnerDecisionRequest("Approved");
    }

    @Test
    void createApplication_Success() {
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        lenient().when(partnerRepository.existsByApplicantIdAndStatusNot(anyLong(), any())).thenReturn(false);
        lenient().when(partnerRepository.existsByTaxCode(anyString())).thenReturn(false);
        lenient().when(partnerRepository.save(any(Partner.class))).thenAnswer(invocation -> {
            Partner saved = invocation.getArgument(0);
            saved.setId(10L);
            return saved;
        });

        var response = partnerService.createApplication(appRequest);

        assertNotNull(response);
        assertEquals("Test Partner", response.name());
        assertEquals(PartnerStatus.DRAFT, response.status());
        verify(auditEventRepository).save(any(PartnerAuditEvent.class));
    }

    @Test
    void createApplication_DuplicateActiveApplication_ThrowsConflict() {
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        when(partnerRepository.existsByApplicantIdAndStatusNot(user.getId(), PartnerStatus.TERMINATED)).thenReturn(true);

        assertThrows(ConflictException.class, () -> partnerService.createApplication(appRequest));
        verify(partnerRepository, never()).save(any(Partner.class));
    }

    @Test
    void createApplication_DuplicateTaxCode_ThrowsConflict() {
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        lenient().when(partnerRepository.existsByApplicantIdAndStatusNot(anyLong(), any())).thenReturn(false);
        when(partnerRepository.existsByTaxCode(appRequest.taxCode())).thenReturn(true);

        assertThrows(ConflictException.class, () -> partnerService.createApplication(appRequest));
        verify(partnerRepository, never()).save(any(Partner.class));
    }

    @Test
    void submitApplication_Success() {
        partner.setStatus(PartnerStatus.DRAFT);
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        lenient().when(partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(partner));
        lenient().when(partnerRepository.save(any(Partner.class))).thenReturn(partner);

        var response = partnerService.submitApplication();

        assertNotNull(response);
        assertEquals(PartnerStatus.PENDING_REVIEW, response.status());
        verify(auditEventRepository).save(any(PartnerAuditEvent.class));
    }

    @Test
    void submitApplication_NoApplication_ThrowsEntityNotFound() {
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        when(partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> partnerService.submitApplication());
    }

    @Test
    void submitApplication_WrongStatus_ThrowsInvalidInput() {
        partner.setStatus(PartnerStatus.APPROVED);
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        lenient().when(partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(partner));

        assertThrows(InvalidInputException.class, () -> partnerService.submitApplication());
    }

    @Test
    void approvePartner_Success() {
        partner.setStatus(PartnerStatus.PENDING_REVIEW);
        Role partnerRole = new Role();
        partnerRole.setId(1L);
        partnerRole.setName("PARTNER");

        lenient().when(authz.getCurrentUser()).thenReturn(admin);
        lenient().when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));
        lenient().when(partnerRepository.save(any(Partner.class))).thenReturn(partner);
        lenient().when(partnerMemberRepository.findByPartnerIdAndUserId(partner.getId(), user.getId()))
                .thenReturn(Optional.empty());
        lenient().when(partnerMemberRepository.save(any(PartnerMember.class))).thenAnswer(invocation -> invocation.getArgument(0));
        lenient().when(roleRepository.findByName("PARTNER")).thenReturn(Optional.of(partnerRole));

        var response = partnerService.approvePartner(partner.getId(), decisionRequest);

        assertNotNull(response);
        assertEquals(PartnerStatus.APPROVED, response.status());
        verify(partnerMemberRepository).save(any(PartnerMember.class));
        verify(auditEventRepository).save(any(PartnerAuditEvent.class));
    }

    @Test
    void approvePartner_DuplicateOwner_DoesNotCreateDuplicateMember() {
        partner.setStatus(PartnerStatus.PENDING_REVIEW);
        PartnerMember existingMember = new PartnerMember();
        existingMember.setRole(PartnerMemberRole.OWNER);

        Role partnerRole = new Role();
        partnerRole.setId(1L);
        partnerRole.setName("PARTNER");

        lenient().when(authz.getCurrentUser()).thenReturn(admin);
        lenient().when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));
        lenient().when(partnerRepository.save(any(Partner.class))).thenReturn(partner);
        lenient().when(partnerMemberRepository.findByPartnerIdAndUserId(partner.getId(), user.getId()))
                .thenReturn(Optional.of(existingMember));
        lenient().when(roleRepository.findByName("PARTNER")).thenReturn(Optional.of(partnerRole));

        var response = partnerService.approvePartner(partner.getId(), decisionRequest);

        assertEquals(PartnerStatus.APPROVED, response.status());
        verify(partnerMemberRepository, never()).save(any(PartnerMember.class));
        verify(auditEventRepository).save(any(PartnerAuditEvent.class));
    }

    @Test
    void approvePartner_NotPendingReview_ThrowsInvalidInput() {
        partner.setStatus(PartnerStatus.DRAFT);
        lenient().when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));

        assertThrows(InvalidInputException.class, () -> partnerService.approvePartner(partner.getId(), decisionRequest));
    }

    @Test
    void approvePartner_PartnerNotFound_ThrowsEntityNotFound() {
        when(partnerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> partnerService.approvePartner(999L, decisionRequest));
    }

    @Test
    void rejectPartner_Success() {
        partner.setStatus(PartnerStatus.PENDING_REVIEW);
        lenient().when(authz.getCurrentUser()).thenReturn(admin);
        lenient().when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));
        lenient().when(partnerRepository.save(any(Partner.class))).thenReturn(partner);

        var response = partnerService.rejectPartner(partner.getId(), decisionRequest);

        assertNotNull(response);
        assertEquals(PartnerStatus.REJECTED, response.status());
        assertEquals("Approved", response.rejectionReason());
        verify(auditEventRepository).save(any(PartnerAuditEvent.class));
    }

    @Test
    void rejectPartner_WrongStatus_ThrowsInvalidInput() {
        partner.setStatus(PartnerStatus.APPROVED);
        lenient().when(partnerRepository.findById(partner.getId())).thenReturn(Optional.of(partner));

        assertThrows(InvalidInputException.class, () -> partnerService.rejectPartner(partner.getId(), decisionRequest));
    }

    @Test
    void rejectPartner_NotFound_ThrowsEntityNotFound() {
        when(partnerRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> partnerService.rejectPartner(999L, decisionRequest));
    }

    @Test
    void getMyProfile_Success() {
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        lenient().when(partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(partner));

        var response = partnerService.getMyProfile();

        assertNotNull(response);
        assertEquals("Test Partner", response.name());
    }

    @Test
    void getMyProfile_NoApplication_ThrowsEntityNotFound() {
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        when(partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> partnerService.getMyProfile());
    }

    @Test
    void getMyStatus_Success() {
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        lenient().when(partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId()))
                .thenReturn(Optional.of(partner));

        var response = partnerService.getMyStatus();

        assertNotNull(response);
        assertEquals(PartnerStatus.DRAFT, response.status());
    }

    @Test
    void getMyStatus_NoApplication_ThrowsEntityNotFound() {
        lenient().when(authz.getCurrentUser()).thenReturn(user);
        when(partnerRepository.findFirstByApplicantIdOrderByCreatedAtDesc(user.getId())).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> partnerService.getMyStatus());
    }
}
