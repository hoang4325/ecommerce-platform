package com.yashmerino.ecommerce.model.dto.partner;

import com.yashmerino.ecommerce.model.partner.PartnerBankAccount;

import java.time.LocalDateTime;

public record PartnerBankAccountResponse(
        Long id,
        Long partnerId,
        String bankName,
        String accountName,
        String maskedAccountNumber,
        String status,
        LocalDateTime verifiedAt
) {
    public static PartnerBankAccountResponse from(PartnerBankAccount account) {
        return new PartnerBankAccountResponse(
                account.getId(), account.getPartner().getId(), account.getBankName(), account.getAccountName(),
                account.getMaskedAccountNumber(), account.getStatus(), account.getVerifiedAt());
    }
}
