package com.yashmerino.ecommerce.model.dto.order;

import java.math.BigDecimal;

public record PartnerOrderItemResponse(
        Long id,
        Long productId,
        Long offerId,
        String productName,
        String partnerSku,
        BigDecimal unitPrice,
        Integer quantity,
        BigDecimal lineTotal,
        BigDecimal discountAllocation,
        BigDecimal commissionAmount,
        BigDecimal partnerPayableAmount,
        String currency
) {
}
