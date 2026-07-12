package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.offer.PartnerOfferRequest;
import com.yashmerino.ecommerce.model.dto.offer.PartnerOfferResponse;
import com.yashmerino.ecommerce.services.interfaces.PartnerOfferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partner/offers")
@RequiredArgsConstructor
public class PartnerOfferController {

    private final PartnerOfferService offerService;

    @PostMapping
    public ResponseEntity<PartnerOfferResponse> createOffer(@RequestParam Long partnerId,
                                                             @Valid @RequestBody PartnerOfferRequest request) {
        return new ResponseEntity<>(offerService.createOffer(partnerId, request), HttpStatus.CREATED);
    }

    @PutMapping("/{offerId}")
    public ResponseEntity<PartnerOfferResponse> updateOffer(@RequestParam Long partnerId,
                                                             @PathVariable Long offerId,
                                                             @Valid @RequestBody PartnerOfferRequest request) {
        return ResponseEntity.ok(offerService.updateOffer(partnerId, offerId, request));
    }

    @GetMapping("/{offerId}")
    public ResponseEntity<PartnerOfferResponse> getOffer(@RequestParam Long partnerId,
                                                          @PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.getOffer(partnerId, offerId));
    }

    @GetMapping
    public ResponseEntity<Page<PartnerOfferResponse>> getOffers(@RequestParam Long partnerId,
                                                                 Pageable pageable) {
        return ResponseEntity.ok(offerService.getOffers(partnerId, pageable));
    }

    @PostMapping("/{offerId}/submit")
    public ResponseEntity<PartnerOfferResponse> submitOffer(@RequestParam Long partnerId,
                                                             @PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.submitOffer(partnerId, offerId));
    }

    @PostMapping("/{offerId}/archive")
    public ResponseEntity<PartnerOfferResponse> archiveOffer(@RequestParam Long partnerId,
                                                              @PathVariable Long offerId) {
        return ResponseEntity.ok(offerService.archiveOffer(partnerId, offerId));
    }

    @PostMapping("/{offerId}/inventory-adjustments")
    public ResponseEntity<PartnerOfferResponse> adjustInventory(@RequestParam Long partnerId,
                                                                 @PathVariable Long offerId,
                                                                 @RequestParam int delta,
                                                                 @RequestParam(defaultValue = "manual_adjustment") String reason) {
        return ResponseEntity.ok(offerService.adjustInventory(partnerId, offerId, delta, reason));
    }
}
