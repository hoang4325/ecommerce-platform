package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.partner.PartnerBankAccountRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerBankAccountResponse;
import com.yashmerino.ecommerce.services.PartnerBankAccountService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/partners/{partnerId}/bank-accounts")
@RequiredArgsConstructor
public class PartnerBankAccountController {
    private final PartnerBankAccountService service;

    @GetMapping
    public ResponseEntity<List<PartnerBankAccountResponse>> list(@PathVariable Long partnerId) {
        return ResponseEntity.ok(service.list(partnerId));
    }

    @PostMapping
    public ResponseEntity<PartnerBankAccountResponse> create(@PathVariable Long partnerId,
                                                              @Valid @RequestBody PartnerBankAccountRequest request) {
        return new ResponseEntity<>(service.create(partnerId, request), HttpStatus.CREATED);
    }
}
