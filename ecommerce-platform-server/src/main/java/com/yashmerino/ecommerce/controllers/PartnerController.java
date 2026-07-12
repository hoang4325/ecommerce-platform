package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.partner.PartnerApplicationRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerProfileUpdateRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerResponse;
import com.yashmerino.ecommerce.model.dto.SuccessDTO;
import com.yashmerino.ecommerce.services.interfaces.PartnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partners")
@RequiredArgsConstructor
public class PartnerController {

    private final PartnerService partnerService;

    @PostMapping("/applications")
    public ResponseEntity<PartnerResponse> createApplication(@Valid @RequestBody PartnerApplicationRequest request) {
        PartnerResponse response = partnerService.createApplication(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/me")
    public ResponseEntity<PartnerResponse> getMyProfile() {
        return ResponseEntity.ok(partnerService.getMyProfile());
    }

    @PutMapping("/me")
    public ResponseEntity<PartnerResponse> updateMyProfile(@Valid @RequestBody PartnerProfileUpdateRequest request) {
        return ResponseEntity.ok(partnerService.updateMyProfile(request));
    }

    @GetMapping("/me/status")
    public ResponseEntity<PartnerResponse> getMyStatus() {
        return ResponseEntity.ok(partnerService.getMyStatus());
    }

    @PostMapping("/me/submit")
    public ResponseEntity<PartnerResponse> submitApplication() {
        return ResponseEntity.ok(partnerService.submitApplication());
    }
}
