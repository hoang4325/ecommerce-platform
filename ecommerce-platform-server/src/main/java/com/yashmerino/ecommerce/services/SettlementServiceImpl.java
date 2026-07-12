package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.dto.settlement.SettlementAdjustmentRequest;
import com.yashmerino.ecommerce.model.dto.settlement.SettlementResponse;
import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
import com.yashmerino.ecommerce.model.settlement.Settlement;
import com.yashmerino.ecommerce.model.settlement.SettlementLine;
import com.yashmerino.ecommerce.model.settlement.SettlementStatus;
import com.yashmerino.ecommerce.repositories.PartnerOrderRepository;
import com.yashmerino.ecommerce.repositories.SettlementLineRepository;
import com.yashmerino.ecommerce.repositories.SettlementRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import com.yashmerino.ecommerce.services.interfaces.SettlementService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementLineRepository settlementLineRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final PartnerAuthorizationService authz;

    @Override
    @Transactional
    public SettlementResponse calculateSettlement(Long partnerId, LocalDateTime periodStart,
                                                   LocalDateTime periodEnd, String currency) {
        authz.requireSettlementRead(partnerId);
        return doCalculateSettlement(partnerId, periodStart, periodEnd, currency);
    }

    @Override
    @Transactional
    public SettlementResponse adminCalculateSettlement(Long partnerId, LocalDateTime periodStart,
                                                        LocalDateTime periodEnd, String currency) {
        return doCalculateSettlement(partnerId, periodStart, periodEnd, currency);
    }

    private SettlementResponse doCalculateSettlement(Long partnerId, LocalDateTime periodStart,
                                                      LocalDateTime periodEnd, String currency) {
        String resolvedCurrency = currency != null ? currency : "USD";

        Settlement settlement = settlementRepository
                .findByPartnerIdAndPeriodStartAndPeriodEndAndCurrency(partnerId, periodStart, periodEnd, resolvedCurrency)
                .orElse(null);

        if (settlement != null) {
            if (settlement.getStatus() == SettlementStatus.PAID) {
                throw new ConflictException("settlement_already_paid");
            }
            settlementLineRepository.deleteBySettlementId(settlement.getId());
            settlementLineRepository.flush();
        } else {
            settlement = new Settlement();
            settlement.setPartner(new com.yashmerino.ecommerce.model.partner.Partner());
            settlement.getPartner().setId(partnerId);
            settlement.setPeriodStart(periodStart);
            settlement.setPeriodEnd(periodEnd);
            settlement.setCurrency(resolvedCurrency);
        }

        settlement.setStatus(SettlementStatus.CALCULATED);
        settlement = settlementRepository.save(settlement);

        List<PartnerOrder> deliveredOrders = partnerOrderRepository
                .findByPartnerIdAndStatusAndDeliveredAtInRange(
                        partnerId, PartnerOrderStatus.DELIVERED, periodStart, periodEnd);

        BigDecimal grossSales = BigDecimal.ZERO;
        BigDecimal commissionAmount = BigDecimal.ZERO;

        for (PartnerOrder po : deliveredOrders) {
            grossSales = grossSales.add(po.getSubtotal());
            commissionAmount = commissionAmount.add(po.getCommissionAmount());

            SettlementLine line = new SettlementLine();
            line.setSettlement(settlement);
            line.setPartner(po.getPartner());
            line.setLineType("SALE");
            line.setOrderId(po.getOrder().getId());
            line.setPartnerOrderId(po.getId());
            line.setAmount(po.getSubtotal());
            line.setCurrency(po.getCurrency());
            line.setIdempotencyKey("settlement-line:" + settlement.getId() + ":" + po.getId());
            settlementLineRepository.save(line);
        }

        settlement.setGrossSales(grossSales);
        settlement.setCommissionAmount(commissionAmount);
        settlement.setPayableAmount(grossSales.subtract(commissionAmount));
        settlement = settlementRepository.save(settlement);

        if (!deliveredOrders.isEmpty()) {
            List<Long> orderIds = deliveredOrders.stream().map(PartnerOrder::getId).toList();
            partnerOrderRepository.markAsSettled(settlement.getId(), orderIds);
        }

        return SettlementResponse.from(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SettlementResponse> getSettlements(Long partnerId, Pageable pageable) {
        authz.requireSettlementRead(partnerId);
        return settlementRepository.findByPartnerId(partnerId, pageable).map(SettlementResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SettlementResponse> adminGetSettlements(Long partnerId, Pageable pageable) {
        return settlementRepository.findByPartnerId(partnerId, pageable).map(SettlementResponse::from);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementResponse getSettlement(Long partnerId, Long settlementId) {
        authz.requireSettlementRead(partnerId);
        Settlement settlement = settlementRepository.findByIdAndPartnerId(settlementId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));
        return SettlementResponse.from(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementResponse adminGetSettlement(Long partnerId, Long settlementId) {
        Settlement settlement = settlementRepository.findByIdAndPartnerId(settlementId, partnerId)
                .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));
        return SettlementResponse.from(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public SettlementResponse getSettlementById(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));
        return SettlementResponse.from(settlement);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<SettlementResponse> getAllSettlements(Pageable pageable) {
        return settlementRepository.findAll(pageable).map(SettlementResponse::from);
    }

    @Override
    @Transactional
    public SettlementResponse approveSettlement(Long settlementId) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));

        if (settlement.getStatus() != SettlementStatus.CALCULATED
                && settlement.getStatus() != SettlementStatus.UNDER_REVIEW) {
            throw new InvalidInputException("cannot_approve_in_current_status");
        }

        settlement.setStatus(SettlementStatus.APPROVED);
        settlement.setApprovedBy(authz.getCurrentUser());
        settlement.setApprovedAt(LocalDateTime.now());
        return SettlementResponse.from(settlementRepository.save(settlement));
    }

    @Override
    @Transactional
    public SettlementResponse markPaid(Long settlementId, String paymentReference) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));

        if (settlement.getStatus() != SettlementStatus.APPROVED) {
            throw new InvalidInputException("cannot_mark_paid_in_current_status");
        }

        settlement.setStatus(SettlementStatus.PAID);
        settlement.setPaidAt(LocalDateTime.now());
        settlement.setPaymentReference(paymentReference);
        return SettlementResponse.from(settlementRepository.save(settlement));
    }

    @Override
    @Transactional
    public SettlementResponse addAdjustment(Long settlementId, SettlementAdjustmentRequest request) {
        Settlement settlement = settlementRepository.findById(settlementId)
                .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));

        if (settlement.getStatus() != SettlementStatus.UNDER_REVIEW
                && settlement.getStatus() != SettlementStatus.CALCULATED) {
            throw new InvalidInputException("cannot_adjust_in_current_status");
        }

        settlement.setManualAdjustment(settlement.getManualAdjustment().add(request.amount()));
        settlement.setPayableAmount(settlement.getPayableAmount().add(request.amount()));
        settlementRepository.save(settlement);

        SettlementLine line = new SettlementLine();
        line.setSettlement(settlement);
        line.setPartner(settlement.getPartner());
        line.setLineType("ADJUSTMENT");
        line.setAmount(request.amount());
        line.setCurrency(settlement.getCurrency());
        line.setAdjustmentReason(request.reason());
        line.setCreatedBy(authz.getCurrentUser().getId());
        settlementLineRepository.save(line);

        return SettlementResponse.from(settlement);
    }
}
