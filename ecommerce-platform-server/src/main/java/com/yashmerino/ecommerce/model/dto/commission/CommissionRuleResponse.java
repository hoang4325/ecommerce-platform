package com.yashmerino.ecommerce.model.dto.commission;

import com.yashmerino.ecommerce.model.commission.CommissionRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record CommissionRuleResponse(
        Long id,
        String name,
        Long partnerId,
        Long categoryId,
        Long productId,
        BigDecimal rate,
        BigDecimal fixedFee,
        String currency,
        Integer priority,
        LocalDateTime validFrom,
        LocalDateTime validTo,
        String status,
        Long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static CommissionRuleResponse from(CommissionRule rule) {
        return new CommissionRuleResponse(
                rule.getId(),
                displayName(rule),
                rule.getPartnerId(),
                rule.getCategoryId(),
                rule.getProductId(),
                rule.getRate(),
                rule.getFixedFee(),
                rule.getCurrency(),
                rule.getPriority(),
                rule.getValidFrom(),
                rule.getValidTo(),
                rule.getStatus(),
                rule.getVersion(),
                rule.getCreatedAt(),
                rule.getUpdatedAt()
        );
    }

    private static String displayName(CommissionRule rule) {
        if (rule.getPartnerId() != null) {
            return "Hoa hồng đối tác #" + rule.getPartnerId();
        }
        if (rule.getCategoryId() != null) {
            return "Hoa hồng danh mục #" + rule.getCategoryId();
        }
        if (rule.getProductId() != null) {
            return "Hoa hồng sản phẩm #" + rule.getProductId();
        }
        return "Hoa hồng toàn sàn";
    }
}
