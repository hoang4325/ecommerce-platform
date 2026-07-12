package com.yashmerino.ecommerce.services.interfaces;

import com.yashmerino.ecommerce.model.dto.settlement.SettlementAdjustmentRequest;
import com.yashmerino.ecommerce.model.dto.settlement.SettlementResponse;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface SettlementService {

    SettlementResponse calculateSettlement(Long partnerId, java.time.LocalDateTime periodStart,
                                           java.time.LocalDateTime periodEnd, String currency);

    Page<SettlementResponse> getSettlements(Long partnerId, Pageable pageable);

    SettlementResponse getSettlement(Long partnerId, Long settlementId);

    SettlementResponse getSettlementById(Long settlementId);

    Page<SettlementResponse> getAllSettlements(Pageable pageable);

    SettlementResponse approveSettlement(Long settlementId);

    SettlementResponse markPaid(Long settlementId, String paymentReference);

    SettlementResponse addAdjustment(Long settlementId, SettlementAdjustmentRequest request);
}
