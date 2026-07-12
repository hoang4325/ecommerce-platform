package com.yashmerino.ecommerce.controllers;

import com.yashmerino.ecommerce.model.dto.partner.PartnerDocumentResponse;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDocumentReviewRequest;
import com.yashmerino.ecommerce.services.PartnerDocumentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/partners/{partnerId}/documents")
@RequiredArgsConstructor
public class AdminPartnerDocumentController {
    private final PartnerDocumentService service;

    @PostMapping("/{documentId}/review")
    public ResponseEntity<PartnerDocumentResponse> review(@PathVariable Long partnerId,
                                                           @PathVariable Long documentId,
                                                           @Valid @RequestBody PartnerDocumentReviewRequest request) {
        return ResponseEntity.ok(service.review(partnerId, documentId, request));
    }
}
