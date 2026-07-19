package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.dto.settlement.SettlementAdjustmentRequest;
import com.yashmerino.ecommerce.model.dto.settlement.SettlementResponse;
import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
import com.yashmerino.ecommerce.model.partner.Partner;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class SettlementServiceImpl implements SettlementService {

    private final SettlementRepository settlementRepository;
    private final SettlementLineRepository settlementLineRepository;
    private final PartnerOrderRepository partnerOrderRepository;
    private final PartnerAuthorizationService authz;
    private final JdbcTemplate jdbc;

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

        // Period overlap check: filter by currency
        List<Settlement> existing = settlementRepository.findByPartnerIdAndCurrency(partnerId, resolvedCurrency, Pageable.unpaged()).getContent();
        for (Settlement s : existing) {
            if (s.getStatus() == SettlementStatus.OPEN) continue;
            if (periodStart.isBefore(s.getPeriodEnd()) && periodEnd.isAfter(s.getPeriodStart())) {
                throw new ConflictException("settlement_period_overlaps_with_id=" + s.getId());
            }
        }

        // Atomic upsert with unique key (partner_id, period_start, period_end, currency)
        jdbc.update(
                "INSERT INTO settlements(partner_id, period_start, period_end, currency, status, gross_sales, " +
                "commission_amount, refund_amount, manual_adjustment, payable_amount, version, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, 'OPEN', 0, 0, 0, 0, 0, 0, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6)) " +
                "ON DUPLICATE KEY UPDATE id = LAST_INSERT_ID(id)",
                partnerId, periodStart, periodEnd, resolvedCurrency);

        Long settlementId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        // Fix 7: Lock settlement with PESSIMISTIC_WRITE
        Settlement settlement = jdbc.query(
            "SELECT id, partner_id, period_start, period_end, currency, gross_sales, " +
            "commission_amount, refund_amount, manual_adjustment, payable_amount, status, " +
            "approved_by, approved_at, paid_at, payment_reference, version, created_at, updated_at " +
            "FROM settlements WHERE id=? FOR UPDATE",
            rs -> {
                if (!rs.next()) throw new IllegalStateException("settlement_vanished");
                Settlement s = new Settlement();
                s.setId(rs.getLong("id"));
                s.setPeriodStart(rs.getTimestamp("period_start") != null ? rs.getTimestamp("period_start").toLocalDateTime() : null);
                s.setPeriodEnd(rs.getTimestamp("period_end") != null ? rs.getTimestamp("period_end").toLocalDateTime() : null);
                s.setCurrency(rs.getString("currency"));
                s.setGrossSales(rs.getBigDecimal("gross_sales"));
                s.setCommissionAmount(rs.getBigDecimal("commission_amount"));
                s.setRefundAmount(rs.getBigDecimal("refund_amount"));
                s.setManualAdjustment(rs.getBigDecimal("manual_adjustment"));
                s.setPayableAmount(rs.getBigDecimal("payable_amount"));
                s.setStatus(SettlementStatus.valueOf(rs.getString("status")));
                s.setVersion(rs.getLong("version"));
                Partner p = new Partner();
                p.setId(partnerId);
                s.setPartner(p);
                return s;
            },
            settlementId);

        if (settlement.getStatus() == SettlementStatus.CALCULATED
                || settlement.getStatus() == SettlementStatus.UNDER_REVIEW
                || settlement.getStatus() == SettlementStatus.APPROVED
                || settlement.getStatus() == SettlementStatus.PAID) {
            throw new ConflictException("settlement_already_calculated");
        }

        settlement.setStatus(SettlementStatus.CALCULATED);

        // Atomic claim: lock only UNSETTLED DELIVERED orders within the period
        List<PartnerOrder> deliveredOrders = partnerOrderRepository
                .findByPartnerIdAndStatusAndDeliveredAtInRangeAndCurrencyAndUnsettledForUpdate(
                        partnerId, PartnerOrderStatus.DELIVERED, periodStart, periodEnd, resolvedCurrency)
                .stream()
                .filter(po -> resolvedCurrency.equals(po.getCurrency()))
                .toList();

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

        // Fix 3: Query PENDING and PARTIALLY_APPLIED, use remaining_amount
        BigDecimal carryForwardAmount = BigDecimal.ZERO;
        BigDecimal currentPayable = grossSales.subtract(commissionAmount);
        List<PendingAdjustmentRow> pendings = jdbc.query(
                "SELECT id,amount,original_amount,applied_amount,remaining_amount,idempotency_key,version,status " +
                "FROM pending_settlement_adjustments " +
                "WHERE partner_id=? AND currency=? AND status IN ('PENDING','PARTIALLY_APPLIED') ORDER BY id FOR UPDATE",
                (rs, n) -> {
                    String adjStatus = rs.getString("status");
                    BigDecimal effectiveAmount = "PARTIALLY_APPLIED".equals(adjStatus)
                        ? rs.getBigDecimal("remaining_amount")
                        : rs.getBigDecimal("amount");
                    return new PendingAdjustmentRow(
                        rs.getLong("id"),
                        rs.getBigDecimal("amount"),
                        rs.getBigDecimal("original_amount"),
                        rs.getBigDecimal("applied_amount"),
                        rs.getBigDecimal("remaining_amount"),
                        effectiveAmount,
                        rs.getString("idempotency_key"),
                        rs.getLong("version"),
                        adjStatus);
                },
                partnerId, resolvedCurrency);

        for (PendingAdjustmentRow p : pendings) {
            BigDecimal availableForApply = currentPayable.max(BigDecimal.ZERO);
            BigDecimal appliedAmount = p.effectiveAmount().compareTo(BigDecimal.ZERO) >= 0
                    ? p.effectiveAmount().min(availableForApply)
                    : p.effectiveAmount().max(availableForApply.negate());

            Long appliedLineId = null;
            if (appliedAmount.compareTo(BigDecimal.ZERO) != 0) {
                SettlementLine line = new SettlementLine();
                line.setSettlement(settlement);
                line.setPartner(settlement.getPartner());
                line.setLineType("CARRY_FORWARD");
                line.setAmount(appliedAmount);
                line.setCurrency(resolvedCurrency);
                line.setIdempotencyKey("CARRY_FORWARD:" + p.id());
                settlementLineRepository.save(line);

                carryForwardAmount = carryForwardAmount.add(appliedAmount);
                currentPayable = currentPayable.add(appliedAmount);
                appliedLineId = line.getId();
            }

            // Fix 3: Accumulate applied, don't overwrite
            BigDecimal newAppliedAmount = (p.appliedAmount() != null ? p.appliedAmount() : BigDecimal.ZERO)
                .add(appliedAmount.compareTo(BigDecimal.ZERO) >= 0 ? appliedAmount : appliedAmount.negate());
            BigDecimal originalAmt = p.originalAmount() != null ? p.originalAmount() : p.amount();
            BigDecimal newRemainingAmount = originalAmt.subtract(newAppliedAmount);

            String newStatus = newRemainingAmount.compareTo(BigDecimal.ZERO) == 0 ? "APPLIED" : "PARTIALLY_APPLIED";
            int changed = jdbc.update(
                    "UPDATE pending_settlement_adjustments SET status=?, claimed_settlement_id=?, applied_line_id=?, " +
                    "applied_amount=?, remaining_amount=?, updated_at=CURRENT_TIMESTAMP(6), version=version+1 " +
                    "WHERE id=? AND version=?",
                    newStatus, settlement.getId(), appliedLineId, newAppliedAmount, newRemainingAmount,
                    p.id(), p.version());
            if (changed != 1) throw new ConflictException("pending_adjustment_claim_conflict");
        }

        // Full payable formula (Policy B: never negative, carry-forward residual debt)
        BigDecimal refundAmount = BigDecimal.ZERO;
        BigDecimal manualAdjustment = BigDecimal.ZERO;

        BigDecimal rawPayable = grossSales
                .subtract(commissionAmount)
                .subtract(refundAmount)
                .add(manualAdjustment)
                .add(carryForwardAmount);
        BigDecimal finalPayable = rawPayable.max(BigDecimal.ZERO);
        BigDecimal residualDebt = rawPayable.compareTo(BigDecimal.ZERO) < 0 ? rawPayable.negate() : BigDecimal.ZERO;

        settlement.setGrossSales(grossSales);
        settlement.setCommissionAmount(commissionAmount);
        settlement.setRefundAmount(refundAmount);
        settlement.setManualAdjustment(manualAdjustment);
        settlement.setPayableAmount(finalPayable);

        if (residualDebt.compareTo(BigDecimal.ZERO) > 0) {
            String ck = "RESIDUAL_DEBT:" + settlement.getId();
            jdbc.update("INSERT INTO pending_settlement_adjustments(partner_id,partner_order_id,refund_id,order_id,amount,original_amount,currency,reason,idempotency_key) " +
                            "VALUES (?,NULL,NULL,NULL,?,?,?,'residual-debt-carried-forward',?) ON DUPLICATE KEY UPDATE id=id",
                    partnerId, residualDebt.negate(), residualDebt.negate(), resolvedCurrency, ck);
        }

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
        Settlement settlement = jdbc.query(
            "SELECT id,status,version FROM settlements WHERE id=? FOR UPDATE",
            rs -> {
                if (!rs.next()) throw new EntityNotFoundException("settlement_not_found");
                Settlement s = new Settlement();
                s.setId(rs.getLong("id"));
                s.setStatus(SettlementStatus.valueOf(rs.getString("status")));
                s.setVersion(rs.getLong("version"));
                return s;
            },
            settlementId);

        if (settlement.getStatus() != SettlementStatus.CALCULATED
                && settlement.getStatus() != SettlementStatus.UNDER_REVIEW) {
            throw new InvalidInputException("cannot_approve_in_current_status");
        }

        int updated = jdbc.update(
            "UPDATE settlements SET status='APPROVED', approved_by=?, approved_at=CURRENT_TIMESTAMP(6), " +
            "version=version+1, updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND version=?",
            authz.getCurrentUser().getId(), settlementId, settlement.getVersion());
        if (updated != 1) throw new ConflictException("settlement_version_conflict");

        Settlement saved = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));
        return SettlementResponse.from(saved);
    }

    @Override
    @Transactional
    public SettlementResponse markPaid(Long settlementId, String paymentReference) {
        Settlement settlement = jdbc.query(
            "SELECT id,status,version FROM settlements WHERE id=? FOR UPDATE",
            rs -> {
                if (!rs.next()) throw new EntityNotFoundException("settlement_not_found");
                Settlement s = new Settlement();
                s.setId(rs.getLong("id"));
                s.setStatus(SettlementStatus.valueOf(rs.getString("status")));
                s.setVersion(rs.getLong("version"));
                return s;
            },
            settlementId);

        if (settlement.getStatus() != SettlementStatus.APPROVED) {
            throw new InvalidInputException("cannot_mark_paid_in_current_status");
        }

        int updated = jdbc.update(
            "UPDATE settlements SET status='PAID', paid_at=CURRENT_TIMESTAMP(6), payment_reference=?, " +
            "version=version+1, updated_at=CURRENT_TIMESTAMP(6) WHERE id=? AND version=?",
            paymentReference, settlementId, settlement.getVersion());
        if (updated != 1) throw new ConflictException("settlement_version_conflict");

        Settlement saved = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));
        return SettlementResponse.from(saved);
    }

    @Override
    @Transactional
    public SettlementResponse addAdjustment(Long settlementId, SettlementAdjustmentRequest request) {
        // FIX 8: Load with PESSIMISTIC_WRITE lock
        Settlement settlement = jdbc.query(
            "SELECT id, partner_id, period_start, period_end, currency, gross_sales, " +
            "commission_amount, refund_amount, manual_adjustment, payable_amount, status, " +
            "approved_by, approved_at, paid_at, payment_reference, version, created_at, updated_at " +
            "FROM settlements WHERE id=? FOR UPDATE",
            rs -> {
                if (!rs.next()) throw new EntityNotFoundException("settlement_not_found");
                Settlement s = new Settlement();
                s.setId(rs.getLong("id"));
                s.setPeriodStart(rs.getTimestamp("period_start") != null ? rs.getTimestamp("period_start").toLocalDateTime() : null);
                s.setPeriodEnd(rs.getTimestamp("period_end") != null ? rs.getTimestamp("period_end").toLocalDateTime() : null);
                s.setCurrency(rs.getString("currency"));
                s.setGrossSales(rs.getBigDecimal("gross_sales"));
                s.setCommissionAmount(rs.getBigDecimal("commission_amount"));
                s.setRefundAmount(rs.getBigDecimal("refund_amount"));
                s.setManualAdjustment(rs.getBigDecimal("manual_adjustment"));
                s.setPayableAmount(rs.getBigDecimal("payable_amount"));
                s.setStatus(SettlementStatus.valueOf(rs.getString("status")));
                s.setVersion(rs.getLong("version"));
                Partner p = new Partner();
                p.setId(rs.getLong("partner_id"));
                s.setPartner(p);
                return s;
            },
            settlementId);

        if (settlement.getStatus() != SettlementStatus.UNDER_REVIEW
                && settlement.getStatus() != SettlementStatus.CALCULATED) {
            throw new InvalidInputException("cannot_adjust_in_current_status");
        }

        // Idempotency check for adjustment
        if (request.idempotencyKey() != null && !request.idempotencyKey().isBlank()) {
            boolean alreadyExists = jdbc.queryForObject(
                "SELECT COUNT(1) FROM settlement_lines WHERE idempotency_key=? AND line_type='ADJUSTMENT'",
                Integer.class, request.idempotencyKey()) > 0;
            if (alreadyExists) {
                return SettlementResponse.from(settlement);
            }
        }

        // FIX 8: Guard payable against negative
        BigDecimal newManualAdjustment = settlement.getManualAdjustment().add(request.amount());
        BigDecimal rawPayable = settlement.getPayableAmount().add(request.amount());
        BigDecimal finalPayable = rawPayable.max(BigDecimal.ZERO);
        BigDecimal residual = rawPayable.compareTo(BigDecimal.ZERO) < 0 ? rawPayable.negate() : BigDecimal.ZERO;

        settlement.setManualAdjustment(newManualAdjustment);
        settlement.setPayableAmount(finalPayable);

        int updated = jdbc.update(
            "UPDATE settlements SET manual_adjustment=?, payable_amount=?, version=version+1, updated_at=CURRENT_TIMESTAMP(6) " +
            "WHERE id=? AND version=?",
            newManualAdjustment, finalPayable, settlementId, settlement.getVersion());
        if (updated != 1) throw new ConflictException("settlement_version_conflict");

        SettlementLine line = new SettlementLine();
        line.setSettlement(settlement);
        line.setPartner(settlement.getPartner());
        line.setLineType("ADJUSTMENT");
        line.setAmount(request.amount());
        line.setCurrency(settlement.getCurrency());
        line.setAdjustmentReason(request.reason());
        line.setCreatedBy(authz.getCurrentUser().getId());
        if (request.idempotencyKey() != null) {
            line.setIdempotencyKey(request.idempotencyKey());
        }
        settlementLineRepository.save(line);

        // Create carry-forward for residual (negative payable after adjustment)
        if (residual.compareTo(BigDecimal.ZERO) > 0) {
            String cfKey = "ADJ_CF:" + settlementId + ":" + (request.idempotencyKey() != null ? request.idempotencyKey() : settlementId);
            jdbc.update("INSERT INTO pending_settlement_adjustments(partner_id,amount,original_amount,currency,reason,idempotency_key) " +
                    "VALUES (?,?,?,?,'adjustment-residual-carried-forward',?) ON DUPLICATE KEY UPDATE id=id",
                    settlement.getPartner().getId(), residual.negate(), residual.negate(), settlement.getCurrency(), cfKey);
        }

        Settlement saved = settlementRepository.findById(settlementId)
            .orElseThrow(() -> new EntityNotFoundException("settlement_not_found"));
        return SettlementResponse.from(saved);
    }

    record PendingAdjustmentRow(Long id, BigDecimal amount, BigDecimal originalAmount,
                                 BigDecimal appliedAmount, BigDecimal remainingAmount,
                                 BigDecimal effectiveAmount, String idempotencyKey,
                                 Long version, String status) {}
}
