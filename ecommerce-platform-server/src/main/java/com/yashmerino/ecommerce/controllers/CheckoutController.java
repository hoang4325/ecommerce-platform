package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.CancelRequestDTO;
import com.yashmerino.ecommerce.model.dto.CheckoutRequestDTO;
import com.yashmerino.ecommerce.model.dto.CheckoutResponseDTO;
import com.yashmerino.ecommerce.model.dto.PaymentInitiationRequestDTO;
import com.yashmerino.ecommerce.model.dto.PaymentInitiationResponseDTO;
import com.yashmerino.ecommerce.services.CheckoutService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@Validated
@RequiredArgsConstructor
public class CheckoutController {
    private final CheckoutService checkoutService;

    @PostMapping("/api/orders/checkout")
    public ResponseEntity<CheckoutResponseDTO> checkout(
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[0-9a-fA-F-]{36}") String key,
            @Valid @RequestBody CheckoutRequestDTO request) {
        return new ResponseEntity<>(checkoutService.checkout(parseUuid(key), request), HttpStatus.CREATED);
    }

    @PostMapping("/api/payments/{paymentId}/initiate")
    public ResponseEntity<PaymentInitiationResponseDTO> initiate(
            @PathVariable Long paymentId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[0-9a-fA-F-]{36}") String key,
            @Valid @RequestBody PaymentInitiationRequestDTO request) {
        return ResponseEntity.ok(checkoutService.initiate(paymentId, parseUuid(key), request));
    }

    @PostMapping("/api/orders/{orderId}/cancel")
    public ResponseEntity<Void> cancel(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[0-9a-fA-F-]{36}") String key,
            @Valid @RequestBody CancelRequestDTO request) {
        checkoutService.cancelOrder(orderId, parseUuid(key), request);
        return ResponseEntity.ok().build();
    }

    private UUID parseUuid(String value) {
        try { return UUID.fromString(value); }
        catch (IllegalArgumentException e) { throw new jakarta.validation.ValidationException("invalid_idempotency_key"); }
    }
}
