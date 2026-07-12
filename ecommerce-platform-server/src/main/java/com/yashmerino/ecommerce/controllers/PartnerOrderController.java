package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.order.PartnerOrderResponse;
import com.yashmerino.ecommerce.services.interfaces.PartnerOrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/partner/orders")
@RequiredArgsConstructor
public class PartnerOrderController {

    private final PartnerOrderService partnerOrderService;

    @GetMapping
    public ResponseEntity<Page<PartnerOrderResponse>> getOrders(@RequestParam Long partnerId,
                                                                 Pageable pageable) {
        return ResponseEntity.ok(partnerOrderService.getPartnerOrders(partnerId, pageable));
    }

    @GetMapping("/{partnerOrderId}")
    public ResponseEntity<PartnerOrderResponse> getOrder(@RequestParam Long partnerId,
                                                          @PathVariable Long partnerOrderId) {
        return ResponseEntity.ok(partnerOrderService.getPartnerOrder(partnerId, partnerOrderId));
    }

    @PostMapping("/{partnerOrderId}/accept")
    public ResponseEntity<PartnerOrderResponse> acceptOrder(@RequestParam Long partnerId,
                                                             @PathVariable Long partnerOrderId) {
        return ResponseEntity.ok(partnerOrderService.acceptOrder(partnerId, partnerOrderId));
    }

    @PostMapping("/{partnerOrderId}/reject")
    public ResponseEntity<PartnerOrderResponse> rejectOrder(@RequestParam Long partnerId,
                                                             @PathVariable Long partnerOrderId,
                                                             @RequestParam String reason) {
        return ResponseEntity.ok(partnerOrderService.rejectOrder(partnerId, partnerOrderId, reason));
    }

    @PostMapping("/{partnerOrderId}/packing")
    public ResponseEntity<PartnerOrderResponse> markPacking(@RequestParam Long partnerId,
                                                              @PathVariable Long partnerOrderId) {
        return ResponseEntity.ok(partnerOrderService.markPacking(partnerId, partnerOrderId));
    }

    @PostMapping("/{partnerOrderId}/ready-to-ship")
    public ResponseEntity<PartnerOrderResponse> markReadyToShip(@RequestParam Long partnerId,
                                                                  @PathVariable Long partnerOrderId) {
        return ResponseEntity.ok(partnerOrderService.markReadyToShip(partnerId, partnerOrderId));
    }

    @PostMapping("/{partnerOrderId}/ship")
    public ResponseEntity<PartnerOrderResponse> shipOrder(@RequestParam Long partnerId,
                                                           @PathVariable Long partnerOrderId) {
        return ResponseEntity.ok(partnerOrderService.shipOrder(partnerId, partnerOrderId));
    }
}
