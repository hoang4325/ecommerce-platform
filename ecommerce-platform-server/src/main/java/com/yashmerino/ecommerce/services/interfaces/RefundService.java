package com.yashmerino.ecommerce.services.interfaces;

import java.math.BigDecimal;

public interface RefundService {

    void processRefund(Long partnerId, Long partnerOrderId, BigDecimal refundAmount, String reason);
}
