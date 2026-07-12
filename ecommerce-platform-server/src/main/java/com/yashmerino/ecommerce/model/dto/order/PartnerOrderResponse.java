package com.yashmerino.ecommerce.model.dto.order;

import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PartnerOrderResponse(
        Long id,
        Long orderId,
        Long partnerId,
        PartnerOrderStatus status,
        BigDecimal subtotal,
        BigDecimal discountAllocation,
        BigDecimal shippingAllocation,
        BigDecimal commissionAmount,
        BigDecimal partnerPayableAmount,
        String currency,
        Long settlementId,
        String settlementStatus,
        LocalDateTime acceptedAt,
        LocalDateTime rejectedAt,
        String rejectionReason,
        LocalDateTime packedAt,
        LocalDateTime readyToShipAt,
        LocalDateTime shippedAt,
        LocalDateTime deliveredAt,
        LocalDateTime cancelledAt,
        String cancelReason,
        LocalDateTime createdAt) {

    public static PartnerOrderResponse from(PartnerOrder po) {
        return new PartnerOrderResponse(
                po.getId(), po.getOrder().getId(), po.getPartner().getId(),
                po.getStatus(), po.getSubtotal(), po.getDiscountAllocation(),
                po.getShippingAllocation(), po.getCommissionAmount(),
                po.getPartnerPayableAmount(), po.getCurrency(),
                po.getSettlementId(), po.getSettlementStatus(),
                po.getAcceptedAt(), po.getRejectedAt(), po.getRejectionReason(),
                po.getPackedAt(), po.getReadyToShipAt(), po.getShippedAt(),
                po.getDeliveredAt(), po.getCancelledAt(), po.getCancelReason(),
                po.getCreatedAt());
    }
}
