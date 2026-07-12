package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.partner.PartnerDocumentRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDocumentResponse;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDocumentReviewRequest;
import com.yashmerino.ecommerce.services.PartnerDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/partners/{partnerId}/documents")
@RequiredArgsConstructor
public class PartnerDocumentController {
    private final PartnerDocumentService service;

    @GetMapping
    public ResponseEntity<List<PartnerDocumentResponse>> list(@PathVariable Long partnerId) {
        return ResponseEntity.ok(service.list(partnerId));
    }

    @PostMapping
    public ResponseEntity<PartnerDocumentResponse> create(@PathVariable Long partnerId,
                                                           @Valid @RequestBody PartnerDocumentRequest request) {
        return new ResponseEntity<>(service.create(partnerId, request), HttpStatus.CREATED);
    }
}
