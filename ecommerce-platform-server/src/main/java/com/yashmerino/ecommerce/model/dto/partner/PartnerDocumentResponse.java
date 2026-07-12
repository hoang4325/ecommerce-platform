package com.yashmerino.ecommerce.model.dto.partner;

import com.yashmerino.ecommerce.model.partner.PartnerDocument;

public record PartnerDocumentResponse(Long id, Long partnerId, String documentType, String status, String objectKey,
                                      String originalFileName, String contentType, Long fileSize, String checksum) {
    public static PartnerDocumentResponse from(PartnerDocument document) {
        return new PartnerDocumentResponse(document.getId(), document.getPartner().getId(), document.getDocumentType(),
                document.getStatus(), document.getObjectKey(), document.getOriginalFileName(), document.getContentType(),
                document.getFileSize(), document.getChecksum());
    }
}
