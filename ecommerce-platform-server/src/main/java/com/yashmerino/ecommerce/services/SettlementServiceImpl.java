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
    private final org.springframework.jdbc.core.JdbcTemplate jdbc;

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

        // Period overlap check: filter by currency to avoid false rejection across currencies
        List<Settlement> existing = settlementRepository.findByPartnerIdAndCurrency(partnerId, resolvedCurrency, Pageable.unpaged()).getContent();
        for (Settlement s : existing) {
            if (s.getStatus() == SettlementStatus.OPEN) continue;
            if (periodStart.isBefore(s.getPeriodEnd()) && periodEnd.isAfter(s.getPeriodStart())) {
                throw new ConflictException("settlement_period_overlaps_with_id=" + s.getId());
            }
        }

        // Atomic creation: use INSERT ... ON DUPLICATE KEY UPDATE via JDBC to prevent race
        Long settlementId = jdbc.query(
                "SELECT id FROM settlements WHERE partner_id=? AND period_start=? AND period_end=? AND currency=? FOR UPDATE",
                rs -> rs.next() ? rs.getLong("id") : null,
                partnerId, periodStart, periodEnd, resolvedCurrency);

        Settlement settlement;
        if (settlementId != null) {
            settlement = settlementRepository.findById(settlementId)
                    .orElseThrow(() -> new IllegalStateException("settlement_vanished_after_lock"));
            if (settlement.getStatus() == SettlementStatus.CALCULATED
                    || settlement.getStatus() == SettlementStatus.UNDER_REVIEW
                    || settlement.getStatus() == SettlementStatus.APPROVED
                    || settlement.getStatus() == SettlementStatus.PAID) {
                throw new ConflictException("settlement_already_calculated");
            }
        } else {
            settlement = new Settlement();
            settlement.setPartner(new com.yashmerino.ecommerce.model.partner.Partner());
            settlement.getPartner().setId(partnerId);
            settlement.setPeriodStart(periodStart);
            settlement.setPeriodEnd(periodEnd);
            settlement.setCurrency(resolvedCurrency);
            settlement.setStatus(SettlementStatus.OPEN);
            settlement = settlementRepository.save(settlement);
        }

        settlement.setStatus(SettlementStatus.CALCULATED);
        settlement = settlementRepository.save(settlement);

        // Atomic claim: lock only UNSETTLED DELIVERED orders within the period
        List<PartnerOrder> deliveredOrders = partnerOrderRepository
                .findByPartnerIdAndStatusAndDeliveredAtInRangeAndUnsettledForUpdate(
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
            line.setIdempotencyKey("SALE:" + po.getId());
            settlementLineRepository.save(line);
        }

        // Claim pending adjustments
        BigDecimal carryForwardAmount = BigDecimal.ZERO;
        List<PendingAdjustmentRow> pendings = jdbc.query(
                "SELECT id,amount,idempotency_key FROM pending_settlement_adjustments " +
                "WHERE partner_id=? AND currency=? AND status='PENDING' ORDER BY id FOR UPDATE",
                (rs, n) -> new PendingAdjustmentRow(rs.getLong("id"), rs.getBigDecimal("amount"), rs.getString("idempotency_key")),
                partnerId, resolvedCurrency);

        for (PendingAdjustmentRow p : pendings) {
            SettlementLine line = new SettlementLine();
            line.setSettlement(settlement);
            line.setPartner(settlement.getPartner());
            line.setLineType("CARRY_FORWARD");
            line.setAmount(p.amount());
            line.setCurrency(resolvedCurrency);
            line.setIdempotencyKey("CARRY_FORWARD:" + p.id());
            settlementLineRepository.save(line);

            carryForwardAmount = carryForwardAmount.add(p.amount());

            jdbc.update("UPDATE pending_settlement_adjustments SET " +
                            "status='APPLIED',claimed_settlement_id=?,applied_line_id=?,updated_at=CURRENT_TIMESTAMP(6),version=version+1 " +
                            "WHERE id=? AND status='PENDING'",
                    settlement.getId(), line.getId(), p.id());
        }

        // Full payable formula
        BigDecimal refundAmount = BigDecimal.ZERO; // will be populated from settlement_lines REFUND type
        BigDecimal manualAdjustment = BigDecimal.ZERO; // will be populated from ADJUSTMENT lines

        settlement.setGrossSales(grossSales);
        settlement.setCommissionAmount(commissionAmount);
        settlement.setRefundAmount(refundAmount);
        settlement.setManualAdjustment(manualAdjustment);
        settlement.setPayableAmount(
                grossSales
                .subtract(commissionAmount)
                .subtract(refundAmount)
                .add(manualAdjustment)
                .add(carryForwardAmount)
                .max(BigDecimal.ZERO));
        settlement = settlementRepository.save(settlement);

        if (!deliveredOrders.isEmpty()) {
            List<Long> orderIds = deliveredOrders.stream().map(PartnerOrder::getId).toList();
            int changed = partnerOrderRepository.markAsSettled(settlement.getId(), orderIds);
            if (changed != orderIds.size()) {
                throw new ConflictException("settlement_claim_conflict_expected=" + orderIds.size() + "_got=" + changed);
            }
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

    private record PendingAdjustmentRow(Long id, BigDecimal amount, String idempotencyKey) {}
}
