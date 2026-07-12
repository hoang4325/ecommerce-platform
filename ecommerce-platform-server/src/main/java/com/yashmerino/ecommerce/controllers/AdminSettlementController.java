package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.settlement.SettlementAdjustmentRequest;
import com.yashmerino.ecommerce.model.dto.settlement.SettlementResponse;
import com.yashmerino.ecommerce.services.interfaces.SettlementService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/admin/settlements")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public ResponseEntity<Page<SettlementResponse>> getAllSettlements(Pageable pageable) {
        return ResponseEntity.ok(settlementService.getAllSettlements(pageable));
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<SettlementResponse> getSettlement(@PathVariable Long settlementId) {
        return ResponseEntity.ok(settlementService.getSettlementById(settlementId));
    }

    @PostMapping("/calculate")
    public ResponseEntity<SettlementResponse> calculateSettlement(@RequestParam Long partnerId,
                                                                     @RequestParam String periodStart,
                                                                     @RequestParam String periodEnd,
                                                                     @RequestParam(defaultValue = "USD") String currency) {
        return ResponseEntity.ok(settlementService.adminCalculateSettlement(
                partnerId, LocalDateTime.parse(periodStart), LocalDateTime.parse(periodEnd), currency));
    }

    @PostMapping("/{settlementId}/approve")
    public ResponseEntity<SettlementResponse> approveSettlement(@PathVariable Long settlementId) {
        return ResponseEntity.ok(settlementService.approveSettlement(settlementId));
    }

    @PostMapping("/{settlementId}/mark-paid")
    public ResponseEntity<SettlementResponse> markPaid(@PathVariable Long settlementId,
                                                        @RequestParam String paymentReference) {
        return ResponseEntity.ok(settlementService.markPaid(settlementId, paymentReference));
    }

    @PostMapping("/{settlementId}/adjustments")
    public ResponseEntity<SettlementResponse> addAdjustment(@PathVariable Long settlementId,
                                                             @Valid @RequestBody SettlementAdjustmentRequest request) {
        return ResponseEntity.ok(settlementService.addAdjustment(settlementId, request));
    }
}
