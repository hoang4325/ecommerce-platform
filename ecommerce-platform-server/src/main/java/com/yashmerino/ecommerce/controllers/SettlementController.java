package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.settlement.SettlementResponse;
import com.yashmerino.ecommerce.services.interfaces.SettlementService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partner/settlements")
@RequiredArgsConstructor
public class SettlementController {

    private final SettlementService settlementService;

    @GetMapping
    public ResponseEntity<Page<SettlementResponse>> getSettlements(@RequestParam Long partnerId,
                                                                    Pageable pageable) {
        return ResponseEntity.ok(settlementService.getSettlements(partnerId, pageable));
    }

    @GetMapping("/{settlementId}")
    public ResponseEntity<SettlementResponse> getSettlement(@RequestParam Long partnerId,
                                                             @PathVariable Long settlementId) {
        return ResponseEntity.ok(settlementService.getSettlement(partnerId, settlementId));
    }
}
