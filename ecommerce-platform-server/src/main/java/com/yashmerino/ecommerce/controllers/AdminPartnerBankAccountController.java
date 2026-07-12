package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.partner.PartnerBankAccountResponse;
import com.yashmerino.ecommerce.services.PartnerBankAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/partners/{partnerId}/bank-accounts")
@RequiredArgsConstructor
public class AdminPartnerBankAccountController {
    private final PartnerBankAccountService service;

    @PostMapping("/{accountId}/verify")
    public ResponseEntity<PartnerBankAccountResponse> verify(@PathVariable Long partnerId, @PathVariable Long accountId) {
        return ResponseEntity.ok(service.verify(partnerId, accountId));
    }
}
