package com.yashmerino.ecommerce.services.interfaces;

import java.math.BigDecimal;
import java.util.List;

public interface CommissionService {

    List<CommissionResult> resolveOrderItemCommissions(List<CommissionRequest> items);

    record CommissionRequest(long productId, Long offerId, Long categoryId, Long partnerId, BigDecimal lineTotal, String currency) {}

    record CommissionResult(long productId, Long offerId, Long commissionRuleId, BigDecimal rate, BigDecimal fixedFee, BigDecimal commissionAmount, BigDecimal partnerPayable) {}
}
