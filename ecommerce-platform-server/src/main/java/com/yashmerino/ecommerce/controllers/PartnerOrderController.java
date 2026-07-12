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
                                                             @PathVariable Long partnerOrderId,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.acceptOrder(partnerId, partnerOrderId, idempotencyKey));
    }

    @PostMapping("/{partnerOrderId}/reject")
    public ResponseEntity<PartnerOrderResponse> rejectOrder(@RequestParam Long partnerId,
                                                             @PathVariable Long partnerOrderId,
                                                             @RequestParam String reason,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.rejectOrder(partnerId, partnerOrderId, reason, idempotencyKey));
    }

    @PostMapping("/{partnerOrderId}/packing")
    public ResponseEntity<PartnerOrderResponse> markPacking(@RequestParam Long partnerId,
                                                              @PathVariable Long partnerOrderId,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.markPacking(partnerId, partnerOrderId, idempotencyKey));
    }

    @PostMapping("/{partnerOrderId}/ready-to-ship")
    public ResponseEntity<PartnerOrderResponse> markReadyToShip(@RequestParam Long partnerId,
                                                                  @PathVariable Long partnerOrderId,
                                                                  @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.markReadyToShip(partnerId, partnerOrderId, idempotencyKey));
    }

    @PostMapping("/{partnerOrderId}/ship")
    public ResponseEntity<PartnerOrderResponse> shipOrder(@RequestParam Long partnerId,
                                                           @PathVariable Long partnerOrderId,
                                                           @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.shipOrder(partnerId, partnerOrderId, idempotencyKey));
    }

    @PostMapping("/{partnerOrderId}/deliver")
    public ResponseEntity<PartnerOrderResponse> deliverOrder(@RequestParam Long partnerId,
                                                              @PathVariable Long partnerOrderId,
                                                              @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.deliverOrder(partnerId, partnerOrderId, idempotencyKey));
    }

    @PostMapping("/{partnerOrderId}/cancel")
    public ResponseEntity<PartnerOrderResponse> cancelOrder(@RequestParam Long partnerId,
                                                             @PathVariable Long partnerOrderId,
                                                             @RequestParam String reason,
                                                             @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.cancelOrder(partnerId, partnerOrderId, reason, idempotencyKey));
    }

    @PostMapping("/{partnerOrderId}/return-request")
    public ResponseEntity<PartnerOrderResponse> requestReturn(@RequestParam Long partnerId,
                                                               @PathVariable Long partnerOrderId,
                                                               @RequestParam String reason,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.requestReturn(partnerId, partnerOrderId, reason, idempotencyKey));
    }

    @PostMapping("/{partnerOrderId}/approve-return")
    public ResponseEntity<PartnerOrderResponse> approveReturn(@RequestParam Long partnerId,
                                                               @PathVariable Long partnerOrderId,
                                                               @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        return ResponseEntity.ok(partnerOrderService.approveReturn(partnerId, partnerOrderId, idempotencyKey));
    }
}
