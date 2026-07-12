package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.partner.PartnerDecisionRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerResponse;
import com.yashmerino.ecommerce.services.interfaces.PartnerService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/partners")
@RequiredArgsConstructor
public class PartnerAdminController {

    private final PartnerService partnerService;

    @GetMapping
    public ResponseEntity<Page<PartnerResponse>> getAllPartners(Pageable pageable) {
        return ResponseEntity.ok(partnerService.getAllPartners(pageable));
    }

    @GetMapping("/{partnerId}")
    public ResponseEntity<PartnerResponse> getPartner(@PathVariable Long partnerId) {
        return ResponseEntity.ok(partnerService.getPartner(partnerId));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<PartnerResponse>> getPartnersByStatus(@PathVariable String status, Pageable pageable) {
        return ResponseEntity.ok(partnerService.getPartnersByStatus(status, pageable));
    }

    @PostMapping("/{partnerId}/approve")
    public ResponseEntity<PartnerResponse> approvePartner(@PathVariable Long partnerId,
                                                           @Valid @RequestBody PartnerDecisionRequest request) {
        return ResponseEntity.ok(partnerService.approvePartner(partnerId, request));
    }

    @PostMapping("/{partnerId}/reject")
    public ResponseEntity<PartnerResponse> rejectPartner(@PathVariable Long partnerId,
                                                          @Valid @RequestBody PartnerDecisionRequest request) {
        return ResponseEntity.ok(partnerService.rejectPartner(partnerId, request));
    }

    @PostMapping("/{partnerId}/suspend")
    public ResponseEntity<PartnerResponse> suspendPartner(@PathVariable Long partnerId,
                                                           @Valid @RequestBody PartnerDecisionRequest request) {
        return ResponseEntity.ok(partnerService.suspendPartner(partnerId, request));
    }

    @PostMapping("/{partnerId}/restore")
    public ResponseEntity<PartnerResponse> restorePartner(@PathVariable Long partnerId,
                                                           @Valid @RequestBody PartnerDecisionRequest request) {
        return ResponseEntity.ok(partnerService.restorePartner(partnerId, request));
    }

    @PostMapping("/{partnerId}/terminate")
    public ResponseEntity<PartnerResponse> terminatePartner(@PathVariable Long partnerId,
                                                             @Valid @RequestBody PartnerDecisionRequest request) {
        return ResponseEntity.ok(partnerService.terminatePartner(partnerId, request));
    }
}
