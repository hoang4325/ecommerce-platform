package com.yashmerino.ecommerce.services;

import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.Order;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.settlement.SettlementAdjustmentRequest;
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
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SettlementServiceImplTest {

    @Mock
    private SettlementRepository settlementRepository;

    @Mock
    private SettlementLineRepository settlementLineRepository;

    @Mock
    private PartnerOrderRepository partnerOrderRepository;

    @Mock
    private PartnerAuthorizationService authz;

    @InjectMocks
    private SettlementServiceImpl settlementService;

    @Captor
    private ArgumentCaptor<Settlement> settlementCaptor;

    private static final Long PARTNER_ID = 1L;
    private static final Long SETTLEMENT_ID = 100L;

    private Partner partner;
    private Settlement settlement;
    private LocalDateTime periodStart;
    private LocalDateTime periodEnd;

    @BeforeEach
    void setUp() {
        partner = new Partner();
        partner.setId(PARTNER_ID);

        settlement = new Settlement();
        settlement.setId(SETTLEMENT_ID);
        settlement.setPartner(partner);
        settlement.setPeriodStart(LocalDateTime.of(2025, 1, 1, 0, 0));
        settlement.setPeriodEnd(LocalDateTime.of(2025, 1, 31, 23, 59));
        settlement.setCurrency("USD");
        settlement.setGrossSales(BigDecimal.ZERO);
        settlement.setCommissionAmount(BigDecimal.ZERO);
        settlement.setRefundAmount(BigDecimal.ZERO);
        settlement.setManualAdjustment(BigDecimal.ZERO);
        settlement.setPayableAmount(BigDecimal.ZERO);
        settlement.setStatus(SettlementStatus.CALCULATED);

        periodStart = LocalDateTime.of(2025, 1, 1, 0, 0);
        periodEnd = LocalDateTime.of(2025, 1, 31, 23, 59);
    }

    @Test
    void calculateSettlement_Success() {
        Order order = new Order();
        order.setId(10L);

        PartnerOrder po1 = new PartnerOrder();
        po1.setId(200L);
        po1.setPartner(partner);
        po1.setOrder(order);
        po1.setSubtotal(new BigDecimal("100.00"));
        po1.setCommissionAmount(new BigDecimal("10.00"));
        po1.setCurrency("USD");
        po1.setStatus(PartnerOrderStatus.DELIVERED);

        PartnerOrder po2 = new PartnerOrder();
        po2.setId(201L);
        po2.setPartner(partner);
        po2.setOrder(order);
        po2.setSubtotal(new BigDecimal("50.00"));
        po2.setCommissionAmount(new BigDecimal("5.00"));
        po2.setCurrency("USD");
        po2.setStatus(PartnerOrderStatus.DELIVERED);

        lenient().doNothing().when(authz).requireSettlementRead(PARTNER_ID);

        lenient().when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> {
            Settlement saved = invocation.getArgument(0);
            if (saved.getId() == null) {
                saved.setId(SETTLEMENT_ID);
            }
            return saved;
        });

        lenient().when(partnerOrderRepository.findByPartnerIdAndStatusAndDeliveredAtBetween(
                PARTNER_ID, PartnerOrderStatus.DELIVERED, periodStart, periodEnd))
                .thenReturn(List.of(po1, po2));

        var response = settlementService.calculateSettlement(PARTNER_ID, periodStart, periodEnd, "USD");

        assertNotNull(response);
        assertEquals(new BigDecimal("150.00"), response.grossSales());
        assertEquals(new BigDecimal("15.00"), response.commissionAmount());
        assertEquals(new BigDecimal("135.00"), response.payableAmount());
        assertEquals(SettlementStatus.CALCULATED, response.status());
        verify(settlementLineRepository, times(2)).save(any(SettlementLine.class));
    }

    @Test
    void calculateSettlement_EmptyPeriod_ReturnsZeroSettlement() {
        lenient().doNothing().when(authz).requireSettlementRead(PARTNER_ID);

        lenient().when(settlementRepository.save(any(Settlement.class))).thenAnswer(invocation -> {
            Settlement saved = invocation.getArgument(0);
            saved.setId(SETTLEMENT_ID);
            return saved;
        });

        lenient().when(partnerOrderRepository.findByPartnerIdAndStatusAndDeliveredAtBetween(
                PARTNER_ID, PartnerOrderStatus.DELIVERED, periodStart, periodEnd))
                .thenReturn(List.of());

        var response = settlementService.calculateSettlement(PARTNER_ID, periodStart, periodEnd, "USD");

        assertNotNull(response);
        assertEquals(BigDecimal.ZERO, response.grossSales());
        assertEquals(BigDecimal.ZERO, response.commissionAmount());
        assertEquals(BigDecimal.ZERO, response.payableAmount());
        verify(settlementLineRepository, never()).save(any(SettlementLine.class));
    }

    @Test
    void getSettlements_Success() {
        lenient().doNothing().when(authz).requireSettlementRead(PARTNER_ID);
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Settlement> settlementPage = new PageImpl<>(List.of(settlement));
        lenient().when(settlementRepository.findByPartnerId(PARTNER_ID, pageable)).thenReturn(settlementPage);

        var response = settlementService.getSettlements(PARTNER_ID, pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        verify(authz).requireSettlementRead(PARTNER_ID);
    }

    @Test
    void getSettlement_Success() {
        lenient().doNothing().when(authz).requireSettlementRead(PARTNER_ID);
        lenient().when(settlementRepository.findByIdAndPartnerId(SETTLEMENT_ID, PARTNER_ID))
                .thenReturn(Optional.of(settlement));

        var response = settlementService.getSettlement(PARTNER_ID, SETTLEMENT_ID);

        assertNotNull(response);
        assertEquals(SETTLEMENT_ID, response.id());
    }

    @Test
    void getSettlement_NotFound_ThrowsEntityNotFound() {
        lenient().doNothing().when(authz).requireSettlementRead(PARTNER_ID);
        when(settlementRepository.findByIdAndPartnerId(SETTLEMENT_ID, PARTNER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> settlementService.getSettlement(PARTNER_ID, SETTLEMENT_ID));
    }

    @Test
    void approveSettlement_Success() {
        settlement.setStatus(SettlementStatus.CALCULATED);
        User admin = new User();
        admin.setId(2L);
        lenient().when(settlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(settlement));
        lenient().when(authz.getCurrentUser()).thenReturn(admin);
        lenient().when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);

        var response = settlementService.approveSettlement(SETTLEMENT_ID);

        assertEquals(SettlementStatus.APPROVED, response.status());
        assertNotNull(settlement.getApprovedAt());
        assertNotNull(settlement.getApprovedBy());
        verify(settlementRepository).save(settlement);
    }

    @Test
    void approveSettlement_WrongStatus_ThrowsInvalidInput() {
        settlement.setStatus(SettlementStatus.PAID);
        lenient().when(settlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(settlement));

        assertThrows(InvalidInputException.class, () -> settlementService.approveSettlement(SETTLEMENT_ID));
    }

    @Test
    void addAdjustment_Success() {
        settlement.setStatus(SettlementStatus.CALCULATED);
        settlement.setPayableAmount(new BigDecimal("100.00"));
        settlement.setManualAdjustment(BigDecimal.ZERO);

        User admin = new User();
        admin.setId(2L);

        SettlementAdjustmentRequest adjustmentRequest = new SettlementAdjustmentRequest(
                new BigDecimal("25.00"), "Shipping credit");

        lenient().when(settlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(settlement));
        lenient().when(authz.getCurrentUser()).thenReturn(admin);
        lenient().when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);
        lenient().when(settlementLineRepository.save(any(SettlementLine.class))).thenReturn(null);

        var response = settlementService.addAdjustment(SETTLEMENT_ID, adjustmentRequest);

        assertEquals(new BigDecimal("25.00"), response.manualAdjustment());
        assertEquals(new BigDecimal("125.00"), response.payableAmount());
        verify(settlementLineRepository).save(argThat(line ->
                "ADJUSTMENT".equals(line.getLineType()) && line.getAmount().equals(new BigDecimal("25.00"))));
    }

    @Test
    void addAdjustment_WrongStatus_ThrowsInvalidInput() {
        settlement.setStatus(SettlementStatus.PAID);
        lenient().when(settlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(settlement));

        SettlementAdjustmentRequest req = new SettlementAdjustmentRequest(
                new BigDecimal("10.00"), "adjustment");

        assertThrows(InvalidInputException.class, () -> settlementService.addAdjustment(SETTLEMENT_ID, req));
    }

    @Test
    void markPaid_Success() {
        settlement.setStatus(SettlementStatus.APPROVED);
        lenient().when(settlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(settlement));
        lenient().when(settlementRepository.save(any(Settlement.class))).thenReturn(settlement);

        var response = settlementService.markPaid(SETTLEMENT_ID, "PAY-REF-001");

        assertEquals(SettlementStatus.PAID, response.status());
        assertNotNull(settlement.getPaidAt());
        assertEquals("PAY-REF-001", settlement.getPaymentReference());
        verify(settlementRepository).save(settlement);
    }

    @Test
    void markPaid_WrongStatus_ThrowsInvalidInput() {
        settlement.setStatus(SettlementStatus.CALCULATED);
        lenient().when(settlementRepository.findById(SETTLEMENT_ID)).thenReturn(Optional.of(settlement));

        assertThrows(InvalidInputException.class,
                () -> settlementService.markPaid(SETTLEMENT_ID, "PAY-REF"));
    }

    @Test
    void markPaid_NotFound_ThrowsEntityNotFound() {
        when(settlementRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> settlementService.markPaid(999L, "PAY-REF"));
    }
}
