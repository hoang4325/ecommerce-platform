package com.yashmerino.ecommerce.model.dto.settlement;

import com.yashmerino.ecommerce.model.settlement.Settlement;
import com.yashmerino.ecommerce.model.settlement.SettlementStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record SettlementResponse(
        Long id,
        Long partnerId,
        LocalDateTime periodStart,
        LocalDateTime periodEnd,
        String currency,
        BigDecimal grossSales,
        BigDecimal refundAmount,
        BigDecimal commissionAmount,
        BigDecimal otherFees,
        BigDecimal manualAdjustment,
        BigDecimal payableAmount,
        SettlementStatus status,
        LocalDateTime approvedAt,
        LocalDateTime paidAt,
        String paymentReference,
        Long version,
        LocalDateTime createdAt) {

    public static SettlementResponse from(Settlement s) {
        return new SettlementResponse(
                s.getId(), s.getPartner().getId(), s.getPeriodStart(), s.getPeriodEnd(),
                s.getCurrency(), s.getGrossSales(), s.getRefundAmount(), s.getCommissionAmount(),
                s.getOtherFees(), s.getManualAdjustment(), s.getPayableAmount(), s.getStatus(),
                s.getApprovedAt(), s.getPaidAt(), s.getPaymentReference(),
                s.getVersion(), s.getCreatedAt());
    }
}
