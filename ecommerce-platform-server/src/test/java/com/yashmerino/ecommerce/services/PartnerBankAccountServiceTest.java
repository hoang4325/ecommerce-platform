package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.model.dto.partner.PartnerBankAccountRequest;
import com.yashmerino.ecommerce.model.partner.PartnerBankAccount;
import com.yashmerino.ecommerce.repositories.PartnerBankAccountRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerBankAccountServiceTest {
    @Mock
    private PartnerBankAccountRepository repository;

    @Mock
    private PartnerAuthorizationService authz;

    @InjectMocks
    private PartnerBankAccountService service;

    @Test
    void create_MasksAccountNumberAndDoesNotReturnRawValue() {
        when(repository.save(any(PartnerBankAccount.class))).thenAnswer(invocation -> {
            PartnerBankAccount account = invocation.getArgument(0);
            account.setId(10L);
            return account;
        });

        var response = service.create(1L, new PartnerBankAccountRequest("Bank", "Account", "1234567890"));

        assertEquals("****7890", response.maskedAccountNumber());
        assertFalse(response.maskedAccountNumber().contains("1234567890"));
        verify(authz).requireBankAccountManagement(1L);
    }
}
