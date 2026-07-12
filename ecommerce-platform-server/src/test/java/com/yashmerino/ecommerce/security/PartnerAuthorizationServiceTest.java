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
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerAuthorizationServiceTest {

    @Mock
    private PartnerMemberRepository partnerMemberRepository;

    @Mock
    private PartnerRepository partnerRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private PartnerAuthorizationService authz;

    private static final Long PARTNER_ID = 1L;
    private static final Long USER_ID = 10L;

    private User user;
    private Partner partner;
    private PartnerMember activeMember;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(USER_ID);

        partner = new Partner();
        partner.setId(PARTNER_ID);
        partner.setStatus(PartnerStatus.APPROVED);

        activeMember = new PartnerMember();
        activeMember.setPartner(partner);
        activeMember.setUser(user);
        activeMember.setStatus(PartnerMemberStatus.ACTIVE);
        activeMember.setRole(PartnerMemberRole.OWNER);

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.isAuthenticated()).thenReturn(true);
        lenient().when(authentication.getName()).thenReturn("testuser");
        lenient().when(userRepository.findByUsername("testuser")).thenReturn(Optional.of(user));
    }

    private void setupMember(PartnerMemberRole role, PartnerMemberStatus status) {
        activeMember.setRole(role);
        activeMember.setStatus(status);
        lenient().when(partnerMemberRepository.findByPartnerIdAndUserId(PARTNER_ID, USER_ID))
                .thenReturn(Optional.of(activeMember));
    }

    private void setupActiveMember(PartnerMemberRole role) {
        setupMember(role, PartnerMemberStatus.ACTIVE);
    }

    @Test
    void requireOfferWrite_Owner_Allowed() {
        setupActiveMember(PartnerMemberRole.OWNER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireOfferWrite(PARTNER_ID));
    }

    @Test
    void requireOfferWrite_Manager_Allowed() {
        setupActiveMember(PartnerMemberRole.MANAGER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireOfferWrite(PARTNER_ID));
    }

    @Test
    void requireOfferWrite_ProductStaff_Allowed() {
        setupActiveMember(PartnerMemberRole.PRODUCT_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireOfferWrite(PARTNER_ID));
    }

    @Test
    void requireOfferWrite_OrderStaff_Denied() {
        setupActiveMember(PartnerMemberRole.ORDER_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireOfferWrite(PARTNER_ID));
    }

    @Test
    void requireOfferWrite_FinanceStaff_Denied() {
        setupActiveMember(PartnerMemberRole.FINANCE_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireOfferWrite(PARTNER_ID));
    }

    @Test
    void requireOrderFulfillment_Owner_Allowed() {
        setupActiveMember(PartnerMemberRole.OWNER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireOrderFulfillment(PARTNER_ID));
    }

    @Test
    void requireOrderFulfillment_Manager_Allowed() {
        setupActiveMember(PartnerMemberRole.MANAGER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireOrderFulfillment(PARTNER_ID));
    }

    @Test
    void requireOrderFulfillment_OrderStaff_Allowed() {
        setupActiveMember(PartnerMemberRole.ORDER_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireOrderFulfillment(PARTNER_ID));
    }

    @Test
    void requireOrderFulfillment_ProductStaff_Denied() {
        setupActiveMember(PartnerMemberRole.PRODUCT_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireOrderFulfillment(PARTNER_ID));
    }

    @Test
    void requireOrderFulfillment_FinanceStaff_Denied() {
        setupActiveMember(PartnerMemberRole.FINANCE_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireOrderFulfillment(PARTNER_ID));
    }

    @Test
    void requireSettlementRead_Owner_Allowed() {
        setupActiveMember(PartnerMemberRole.OWNER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireSettlementRead(PARTNER_ID));
    }

    @Test
    void requireSettlementRead_Manager_Allowed() {
        setupActiveMember(PartnerMemberRole.MANAGER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireSettlementRead(PARTNER_ID));
    }

    @Test
    void requireSettlementRead_FinanceStaff_Allowed() {
        setupActiveMember(PartnerMemberRole.FINANCE_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireSettlementRead(PARTNER_ID));
    }

    @Test
    void requireSettlementRead_ProductStaff_Denied() {
        setupActiveMember(PartnerMemberRole.PRODUCT_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireSettlementRead(PARTNER_ID));
    }

    @Test
    void requireMemberManagement_Owner_Allowed() {
        setupActiveMember(PartnerMemberRole.OWNER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireMemberManagement(PARTNER_ID));
    }

    @Test
    void requireMemberManagement_Manager_Allowed() {
        setupActiveMember(PartnerMemberRole.MANAGER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireMemberManagement(PARTNER_ID));
    }

    @Test
    void requireMemberManagement_OrderStaff_Denied() {
        setupActiveMember(PartnerMemberRole.ORDER_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireMemberManagement(PARTNER_ID));
    }

    @Test
    void requireMemberManagement_ProductStaff_Denied() {
        setupActiveMember(PartnerMemberRole.PRODUCT_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireMemberManagement(PARTNER_ID));
    }

    @Test
    void requireMemberManagement_FinanceStaff_Denied() {
        setupActiveMember(PartnerMemberRole.FINANCE_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireMemberManagement(PARTNER_ID));
    }

    @Test
    void requireOfferRead_AnyActiveMember_Allowed() {
        setupActiveMember(PartnerMemberRole.ORDER_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireOfferRead(PARTNER_ID));
    }

    @Test
    void requireOfferRead_InactiveMembership_Denied() {
        setupMember(PartnerMemberRole.OWNER, PartnerMemberStatus.SUSPENDED);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireOfferRead(PARTNER_ID));
    }

    @Test
    void requireOfferWrite_InactiveMembership_Denied() {
        setupMember(PartnerMemberRole.OWNER, PartnerMemberStatus.SUSPENDED);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireOfferWrite(PARTNER_ID));
    }

    @Test
    void requireOrderFulfillment_InactiveMembership_Denied() {
        setupMember(PartnerMemberRole.OWNER, PartnerMemberStatus.INVITED);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireOrderFulfillment(PARTNER_ID));
    }

    @Test
    void requireSettlementRead_InactiveMembership_Denied() {
        setupMember(PartnerMemberRole.OWNER, PartnerMemberStatus.REMOVED);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireSettlementRead(PARTNER_ID));
    }

    @Test
    void requireMemberManagement_InactiveMembership_Denied() {
        setupMember(PartnerMemberRole.OWNER, PartnerMemberStatus.SUSPENDED);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireMemberManagement(PARTNER_ID));
    }

    @Test
    void getActiveMember_NotMember_ThrowsAccessDenied() {
        when(partnerMemberRepository.findByPartnerIdAndUserId(PARTNER_ID, USER_ID)).thenReturn(Optional.empty());

        assertThrows(AccessDeniedException.class, () -> authz.getActiveMember(PARTNER_ID, USER_ID));
    }

    @Test
    void getActiveMember_NotActive_ThrowsAccessDenied() {
        activeMember.setStatus(PartnerMemberStatus.SUSPENDED);
        lenient().when(partnerMemberRepository.findByPartnerIdAndUserId(PARTNER_ID, USER_ID))
                .thenReturn(Optional.of(activeMember));

        assertThrows(AccessDeniedException.class, () -> authz.getActiveMember(PARTNER_ID, USER_ID));
    }

    @Test
    void getActiveMember_Success() {
        activeMember.setStatus(PartnerMemberStatus.ACTIVE);
        lenient().when(partnerMemberRepository.findByPartnerIdAndUserId(PARTNER_ID, USER_ID))
                .thenReturn(Optional.of(activeMember));

        PartnerMember result = authz.getActiveMember(PARTNER_ID, USER_ID);

        assertNotNull(result);
        assertEquals(PartnerMemberStatus.ACTIVE, result.getStatus());
    }

    @Test
    void requireOfferWrite_PartnerNotActive_ThrowsAccessDenied() {
        partner.setStatus(PartnerStatus.SUSPENDED);
        setupActiveMember(PartnerMemberRole.OWNER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireOfferWrite(PARTNER_ID));
    }

    @Test
    void requireOfferWrite_PartnerNotFound_ThrowsEntityNotFound() {
        when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> authz.requireOfferWrite(PARTNER_ID));
    }

    @Test
    void requireOrderRead_AnyActiveMember_Allowed() {
        setupActiveMember(PartnerMemberRole.PRODUCT_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireOrderRead(PARTNER_ID));
    }

    @Test
    void requireInventoryWrite_Owner_Allowed() {
        setupActiveMember(PartnerMemberRole.OWNER);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireInventoryWrite(PARTNER_ID));
    }

    @Test
    void requireInventoryWrite_ProductStaff_Allowed() {
        setupActiveMember(PartnerMemberRole.PRODUCT_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertDoesNotThrow(() -> authz.requireInventoryWrite(PARTNER_ID));
    }

    @Test
    void requireInventoryWrite_OrderStaff_Denied() {
        setupActiveMember(PartnerMemberRole.ORDER_STAFF);
        lenient().when(partnerRepository.findById(PARTNER_ID)).thenReturn(Optional.of(partner));

        assertThrows(AccessDeniedException.class, () -> authz.requireInventoryWrite(PARTNER_ID));
    }
}
