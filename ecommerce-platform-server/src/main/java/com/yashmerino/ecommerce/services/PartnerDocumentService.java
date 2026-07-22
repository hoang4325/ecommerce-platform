package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDocumentRequest;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDocumentResponse;
import com.yashmerino.ecommerce.model.dto.partner.PartnerDocumentReviewRequest;
import com.yashmerino.ecommerce.model.partner.Partner;
import com.yashmerino.ecommerce.model.partner.PartnerDocument;
import com.yashmerino.ecommerce.repositories.PartnerDocumentRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class PartnerDocumentService {
    private static final Set<String> REVIEW_STATUSES = Set.of("APPROVED", "REJECTED");

    private final PartnerDocumentRepository repository;
    private final PartnerAuthorizationService authz;

    @Transactional(readOnly = true)
    public List<PartnerDocumentResponse> list(Long partnerId) {
        authz.requireMembership(partnerId);
        return repository.findByPartnerId(partnerId).stream().map(PartnerDocumentResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public List<PartnerDocumentResponse> listForAdmin(Long partnerId) {
        return repository.findByPartnerId(partnerId).stream().map(PartnerDocumentResponse::from).toList();
    }

    @Transactional
    public PartnerDocumentResponse create(Long partnerId, PartnerDocumentRequest request) {
        authz.requireMembership(partnerId);
        Partner partner = new Partner();
        partner.setId(partnerId);
        PartnerDocument document = new PartnerDocument();
        document.setPartner(partner);
        document.setDocumentType(request.documentType().trim());
        document.setStatus("PENDING_REVIEW");
        document.setObjectKey(request.objectKey().trim());
        document.setOriginalFileName(request.originalFileName().trim());
        document.setContentType(request.contentType().trim());
        document.setFileSize(request.fileSize());
        document.setChecksum(request.checksum().trim());
        document.setUploadedBy(authz.getCurrentUser().getId());
        return PartnerDocumentResponse.from(repository.save(document));
    }

    @Transactional
    public PartnerDocumentResponse review(Long partnerId, Long documentId, PartnerDocumentReviewRequest request) {
        String status = request.status().toUpperCase(java.util.Locale.ROOT);
        if (!REVIEW_STATUSES.contains(status)) throw new InvalidInputException("invalid_document_status");
        PartnerDocument document = repository.findByIdAndPartnerId(documentId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("partner_document_not_found"));
        document.setStatus(status);
        document.setReviewedBy(authz.getCurrentUser().getId());
        document.setReviewedAt(LocalDateTime.now());
        document.setRejectionReason("REJECTED".equals(status) ? request.rejectionReason() : null);
        return PartnerDocumentResponse.from(repository.save(document));
    }
}
