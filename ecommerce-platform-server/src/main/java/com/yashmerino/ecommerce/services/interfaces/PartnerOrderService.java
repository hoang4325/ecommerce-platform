package com.yashmerino.ecommerce.services.interfaces;

import com.yashmerino.ecommerce.model.dto.order.PartnerOrderResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface PartnerOrderService {

    Page<PartnerOrderResponse> getPartnerOrders(Long partnerId, Pageable pageable);

    PartnerOrderResponse getPartnerOrder(Long partnerId, Long partnerOrderId);

    PartnerOrderResponse acceptOrder(Long partnerId, Long partnerOrderId, String idempotencyKey);

    PartnerOrderResponse rejectOrder(Long partnerId, Long partnerOrderId, String reason, String idempotencyKey);

    PartnerOrderResponse markPacking(Long partnerId, Long partnerOrderId, String idempotencyKey);

    PartnerOrderResponse markReadyToShip(Long partnerId, Long partnerOrderId, String idempotencyKey);

    PartnerOrderResponse shipOrder(Long partnerId, Long partnerOrderId, String idempotencyKey);

    PartnerOrderResponse deliverOrder(Long partnerId, Long partnerOrderId, String idempotencyKey);

    PartnerOrderResponse cancelOrder(Long partnerId, Long partnerOrderId, String reason, String idempotencyKey);

    PartnerOrderResponse requestReturn(Long partnerId, Long partnerOrderId, String reason, String idempotencyKey);

    PartnerOrderResponse approveReturn(Long partnerId, Long partnerOrderId, String idempotencyKey);
}
