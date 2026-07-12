package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.model.dto.partner.PartnerMemberRequest;
import com.yashmerino.ecommerce.model.partner.PartnerMemberRole;
import com.yashmerino.ecommerce.repositories.PartnerMemberRepository;
import com.yashmerino.ecommerce.repositories.UserRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PartnerMemberManagementServiceTest {
    @Mock
    private PartnerMemberRepository memberRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PartnerAuthorizationService authz;

    @InjectMocks
    private PartnerMemberManagementService service;

    @Test
    void invite_RejectsOwnerInvitation() {
        assertThrows(ConflictException.class,
                () -> service.invite(1L, new PartnerMemberRequest(2L, PartnerMemberRole.OWNER)));

        verify(authz).requireMemberManagement(1L);
    }
}
