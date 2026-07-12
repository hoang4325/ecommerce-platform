package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.offer.PartnerOfferResponse;
import com.yashmerino.ecommerce.services.interfaces.PartnerOfferService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/offers")
@RequiredArgsConstructor
public class AdminOfferController {

    private final PartnerOfferService offerService;

    @GetMapping
    public ResponseEntity<Page<PartnerOfferResponse>> getAllOffers(Pageable pageable) {
        return ResponseEntity.ok(offerService.getAllOffers(pageable));
    }

    @GetMapping("/status/{status}")
    public ResponseEntity<Page<PartnerOfferResponse>> getOffersByStatus(@PathVariable String status,
                                                                          Pageable pageable) {
        return ResponseEntity.ok(offerService.getOffersByStatus(status, pageable));
    }

    @PostMapping("/{offerId}/approve")
    public ResponseEntity<PartnerOfferResponse> approveOffer(@PathVariable Long offerId,
                                                              @RequestParam(defaultValue = "approved") String reason) {
        return ResponseEntity.ok(offerService.approveOffer(offerId, reason));
    }

    @PostMapping("/{offerId}/reject")
    public ResponseEntity<PartnerOfferResponse> rejectOffer(@PathVariable Long offerId,
                                                             @RequestParam String reason) {
        return ResponseEntity.ok(offerService.rejectOffer(offerId, reason));
    }

    @PostMapping("/{offerId}/suspend")
    public ResponseEntity<PartnerOfferResponse> suspendOffer(@PathVariable Long offerId,
                                                              @RequestParam String reason) {
        return ResponseEntity.ok(offerService.suspendOffer(offerId, reason));
    }
}
