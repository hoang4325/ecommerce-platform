package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.partner.PartnerMemberRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerMemberResponse;
import com.yashmerino.ecommerce.services.PartnerMemberManagementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/partners/{partnerId}/members")
@RequiredArgsConstructor
public class PartnerMemberController {
    private final PartnerMemberManagementService service;

    @GetMapping
    public ResponseEntity<List<PartnerMemberResponse>> list(@PathVariable Long partnerId) {
        return ResponseEntity.ok(service.list(partnerId));
    }

    @PostMapping
    public ResponseEntity<PartnerMemberResponse> invite(@PathVariable Long partnerId,
                                                         @Valid @RequestBody PartnerMemberRequest request) {
        return new ResponseEntity<>(service.invite(partnerId, request), HttpStatus.CREATED);
    }

    @PostMapping("/{memberId}/activate")
    public ResponseEntity<PartnerMemberResponse> activate(@PathVariable Long partnerId, @PathVariable Long memberId) {
        return ResponseEntity.ok(service.activate(partnerId, memberId));
    }

    @PostMapping("/{memberId}/suspend")
    public ResponseEntity<PartnerMemberResponse> suspend(@PathVariable Long partnerId, @PathVariable Long memberId) {
        return ResponseEntity.ok(service.suspend(partnerId, memberId));
    }

    @PostMapping("/{memberId}/restore")
    public ResponseEntity<PartnerMemberResponse> restore(@PathVariable Long partnerId, @PathVariable Long memberId) {
        return ResponseEntity.ok(service.restore(partnerId, memberId));
    }

    @PostMapping("/{memberId}/transfer-ownership")
    public ResponseEntity<PartnerMemberResponse> transferOwnership(@PathVariable Long partnerId, @PathVariable Long memberId) {
        return ResponseEntity.ok(service.transferOwnership(partnerId, memberId));
    }
}
