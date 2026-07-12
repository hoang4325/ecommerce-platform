package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.model.dto.partner.PartnerBankAccountRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerBankAccountResponse;
import com.yashmerino.ecommerce.model.partner.Partner;
import com.yashmerino.ecommerce.model.partner.PartnerBankAccount;
import com.yashmerino.ecommerce.repositories.PartnerBankAccountRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PartnerBankAccountService {
    private final PartnerBankAccountRepository repository;
    private final PartnerAuthorizationService authz;

    @Transactional(readOnly = true)
    public List<PartnerBankAccountResponse> list(Long partnerId) {
        authz.requireBankAccountManagement(partnerId);
        return repository.findByPartnerId(partnerId).stream().map(PartnerBankAccountResponse::from).toList();
    }

    @Transactional
    public PartnerBankAccountResponse create(Long partnerId, PartnerBankAccountRequest request) {
        authz.requireBankAccountManagement(partnerId);
        Partner partner = new Partner();
        partner.setId(partnerId);
        PartnerBankAccount account = new PartnerBankAccount();
        account.setPartner(partner);
        account.setBankName(request.bankName().trim());
        account.setAccountName(request.accountName().trim());
        account.setMaskedAccountNumber(mask(request.accountNumber()));
        account.setEncryptedAccountNumber(null);
        account.setStatus("PENDING");
        return PartnerBankAccountResponse.from(repository.save(account));
    }

    @Transactional
    public PartnerBankAccountResponse verify(Long partnerId, Long accountId) {
        PartnerBankAccount account = repository.findByIdAndPartnerId(accountId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_bank_account_not_found"));
        account.setStatus("VERIFIED");
        account.setVerifiedAt(LocalDateTime.now());
        account.setVerifiedBy(authz.getCurrentUser());
        return PartnerBankAccountResponse.from(repository.save(account));
    }

    private String mask(String raw) {
        String normalized = raw.replaceAll("\\s+", "");
        int suffix = Math.min(4, normalized.length());
        return "****" + normalized.substring(normalized.length() - suffix);
    }
}
