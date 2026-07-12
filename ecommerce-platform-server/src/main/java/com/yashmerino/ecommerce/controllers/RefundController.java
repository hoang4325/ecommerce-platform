package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.domain.Refund;
import com.yashmerino.ecommerce.model.dto.RefundRequestDTO;
import com.yashmerino.ecommerce.services.RefundService;
import com.yashmerino.ecommerce.services.interfaces.UserService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class RefundController {

    private final RefundService refundService;
    private final UserService userService;

    @PostMapping("/api/orders/{orderId}/refund")
    public ResponseEntity<Refund> requestRefund(
            @PathVariable Long orderId,
            @RequestHeader("Idempotency-Key") java.util.UUID idempotencyKey,
            @Valid @RequestBody RefundRequestDTO dto,
            @AuthenticationPrincipal UserDetails userDetails) {
        User user = userService.getByUsername(userDetails.getUsername());
        Refund refund = refundService.requestRefund(orderId, user.getId(), idempotencyKey, dto);
        return new ResponseEntity<>(refund, HttpStatus.CREATED);
    }
}
