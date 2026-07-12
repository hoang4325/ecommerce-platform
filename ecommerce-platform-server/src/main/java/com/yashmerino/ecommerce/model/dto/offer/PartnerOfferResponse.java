package com.yashmerino.ecommerce.model.dto.offer;

import com.yashmerino.ecommerce.model.offer.PartnerOffer;
import com.yashmerino.ecommerce.model.offer.PartnerOfferStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PartnerOfferResponse(
        Long id,
        Long partnerId,
        Long productId,
        String partnerSku,
        BigDecimal price,
        String currency,
        Integer onHandQuantity,
        Integer reservedQuantity,
        PartnerOfferStatus status,
        LocalDateTime approvedAt,
        LocalDateTime submittedAt,
        String rejectionReason,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt) {

    public static PartnerOfferResponse from(PartnerOffer offer) {
        return new PartnerOfferResponse(
                offer.getId(), offer.getPartner().getId(), offer.getProduct().getId(),
                offer.getPartnerSku(), offer.getPrice(), offer.getCurrency(),
                offer.getOnHandQuantity(), offer.getReservedQuantity(),
                offer.getStatus(), offer.getApprovedAt(), offer.getSubmittedAt(),
                offer.getRejectionReason(), offer.getVersion(),
                offer.getCreatedAt(), offer.getUpdatedAt());
    }
}
