package com.yashmerino.ecommerce.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.exceptions.InvalidInputException;
import com.yashmerino.ecommerce.model.Order;
import com.yashmerino.ecommerce.model.dto.order.PartnerOrderResponse;
import com.yashmerino.ecommerce.model.order.PartnerOrder;
import com.yashmerino.ecommerce.model.order.PartnerOrderStatus;
import com.yashmerino.ecommerce.model.partner.Partner;
import com.yashmerino.ecommerce.repositories.PartnerOrderRepository;
import com.yashmerino.ecommerce.security.PartnerAuthorizationService;
import jakarta.persistence.EntityNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PartnerOrderServiceImplTest {

    @Mock
    private PartnerOrderRepository partnerOrderRepository;

    @Mock
    private PartnerAuthorizationService authz;

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private PartnerOrderServiceImpl partnerOrderService;

    private static final Long PARTNER_ID = 1L;
    private static final Long ORDER_ID = 100L;
    private static final String IDEMPOTENCY_KEY = "idem-1";

    private Partner partner;
    private Order order;
    private PartnerOrder partnerOrder;

    @BeforeEach
    void setUp() throws Exception {
        partner = new Partner();
        partner.setId(PARTNER_ID);

        order = new Order();
        order.setId(10L);

        partnerOrder = new PartnerOrder();
        partnerOrder.setId(ORDER_ID);
        partnerOrder.setPartner(partner);
        partnerOrder.setOrder(order);
        partnerOrder.setStatus(PartnerOrderStatus.NEW);
        partnerOrder.setSubtotal(new BigDecimal("100.00"));
        partnerOrder.setCommissionAmount(new BigDecimal("10.00"));
        partnerOrder.setPartnerPayableAmount(new BigDecimal("90.00"));
        partnerOrder.setCurrency("USD");

        lenient().when(objectMapper.writeValueAsString(any())).thenReturn("{}");
        lenient().when(jdbc.update(startsWith("INSERT INTO partner_order_commands"),
                anyLong(), anyLong(), anyString(), anyString(), anyString())).thenReturn(1);
    }

    private PartnerOrderServiceImpl.PartnerOrderRow createRow(PartnerOrderStatus status, long version) {
        return new PartnerOrderServiceImpl.PartnerOrderRow(
                ORDER_ID, 10L, PARTNER_ID, status, version,
                new BigDecimal("100.00"), BigDecimal.ZERO, BigDecimal.ZERO,
                new BigDecimal("10.00"), new BigDecimal("90.00"), "USD",
                null, null, null, null, null, null, null, null, null, LocalDateTime.now());
    }

    private void stubSelectForUpdate(PartnerOrderStatus status, long version) {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(ORDER_ID), eq(PARTNER_ID)))
                .thenReturn(List.of(createRow(status, version)));
    }

    @Test
    void acceptOrder_Success() {
        stubSelectForUpdate(PartnerOrderStatus.NEW, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.ACCEPTED);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.ACCEPTED, response.status());
    }

    @Test
    void acceptOrder_MissingIdempotencyKey_ThrowsInvalidInput() {
        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID, null));

        verify(jdbc, never()).query(anyString(), any(RowMapper.class), anyLong(), anyLong());
    }

    @Test
    void acceptOrder_WrongStatus_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.SHIPPED, 0L);

        assertThrows(InvalidInputException.class, () -> partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void acceptOrder_NotFound_ThrowsEntityNotFound() {
        when(jdbc.query(anyString(), any(RowMapper.class), eq(ORDER_ID), eq(PARTNER_ID)))
                .thenReturn(List.of());

        assertThrows(EntityNotFoundException.class, () -> partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void rejectOrder_Success() {
        stubSelectForUpdate(PartnerOrderStatus.NEW, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.REJECTED);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyString(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.rejectOrder(PARTNER_ID, ORDER_ID, "Out of stock", IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.REJECTED, response.status());
    }

    @Test
    void rejectOrder_WrongStatus_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.ACCEPTED, 0L);

        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.rejectOrder(PARTNER_ID, ORDER_ID, "reason", IDEMPOTENCY_KEY));
    }

    @Test
    void markPacking_Success() {
        stubSelectForUpdate(PartnerOrderStatus.ACCEPTED, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.PACKING);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.markPacking(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.PACKING, response.status());
    }

    @Test
    void markPacking_WrongStatus_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.NEW, 0L);

        assertThrows(InvalidInputException.class, () -> partnerOrderService.markPacking(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void markReadyToShip_Success() {
        stubSelectForUpdate(PartnerOrderStatus.PACKING, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.READY_TO_SHIP);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.markReadyToShip(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.READY_TO_SHIP, response.status());
    }

    @Test
    void markReadyToShip_WrongStatus_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.ACCEPTED, 0L);

        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.markReadyToShip(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void shipOrder_Success() {
        stubSelectForUpdate(PartnerOrderStatus.READY_TO_SHIP, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.SHIPPED);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.shipOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.SHIPPED, response.status());
    }

    @Test
    void shipOrder_WrongStatus_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.PACKING, 0L);

        assertThrows(InvalidInputException.class, () -> partnerOrderService.shipOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void deliverOrder_Success() {
        stubSelectForUpdate(PartnerOrderStatus.SHIPPED, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.DELIVERED);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.deliverOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.DELIVERED, response.status());
    }

    @Test
    void deliverOrder_WrongStatus_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.READY_TO_SHIP, 0L);

        assertThrows(InvalidInputException.class, () -> partnerOrderService.deliverOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void cancelOrder_FromNew_Success() {
        stubSelectForUpdate(PartnerOrderStatus.NEW, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.CANCELLED);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyString(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.cancelOrder(PARTNER_ID, ORDER_ID, "Customer request", IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.CANCELLED, response.status());
    }

    @Test
    void cancelOrder_FromAccepted_Success() {
        stubSelectForUpdate(PartnerOrderStatus.ACCEPTED, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.CANCELLED);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyString(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.cancelOrder(PARTNER_ID, ORDER_ID, "Supplier issue", IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.CANCELLED, response.status());
    }

    @Test
    void cancelOrder_Shipped_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.SHIPPED, 0L);

        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.cancelOrder(PARTNER_ID, ORDER_ID, "reason", IDEMPOTENCY_KEY));
    }

    @Test
    void requestReturn_Success() {
        stubSelectForUpdate(PartnerOrderStatus.DELIVERED, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.RETURN_REQUESTED);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyString(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.requestReturn(PARTNER_ID, ORDER_ID, "Defective item", IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.RETURN_REQUESTED, response.status());
    }

    @Test
    void requestReturn_WrongStatus_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.SHIPPED, 0L);

        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.requestReturn(PARTNER_ID, ORDER_ID, "reason", IDEMPOTENCY_KEY));
    }

    @Test
    void approveReturn_Success() {
        stubSelectForUpdate(PartnerOrderStatus.RETURN_REQUESTED, 0L);
        partnerOrder.setStatus(PartnerOrderStatus.RETURNED);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        when(jdbc.update(anyString(), any(), anyLong(), any(), anyLong())).thenReturn(1);
        doNothing().when(outboxService).saveOutboxEvent(anyString(), anyString(), anyLong(), anyString(), anyString(), anyString(), any(), anyString());

        var response = partnerOrderService.approveReturn(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.RETURNED, response.status());
    }

    @Test
    void approveReturn_WrongStatus_ThrowsInvalidInput() {
        stubSelectForUpdate(PartnerOrderStatus.DELIVERED, 0L);

        assertThrows(InvalidInputException.class, () -> partnerOrderService.approveReturn(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void getPartnerOrders_Success() {
        doNothing().when(authz).requireOrderRead(PARTNER_ID);
        PageRequest pageable = PageRequest.of(0, 10);
        Page<PartnerOrder> orderPage = new PageImpl<>(List.of(partnerOrder));
        when(partnerOrderRepository.findByPartnerId(PARTNER_ID, pageable)).thenReturn(orderPage);

        Page<PartnerOrderResponse> response = partnerOrderService.getPartnerOrders(PARTNER_ID, pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
    }

    @Test
    void getPartnerOrder_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.NEW);
        doNothing().when(authz).requireOrderRead(PARTNER_ID);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        var response = partnerOrderService.getPartnerOrder(PARTNER_ID, ORDER_ID);

        assertNotNull(response);
        assertEquals(ORDER_ID, response.id());
        assertEquals(PartnerOrderStatus.NEW, response.status());
    }

    @Test
    void getPartnerOrder_NotFound_ThrowsEntityNotFound() {
        doNothing().when(authz).requireOrderRead(PARTNER_ID);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> partnerOrderService.getPartnerOrder(PARTNER_ID, ORDER_ID));
    }

    @Test
    void acceptOrder_VersionConflict_ThrowsConflictException() {
        stubSelectForUpdate(PartnerOrderStatus.NEW, 0L);
        when(jdbc.update(anyString(), any(), anyLong(), any(), anyLong())).thenReturn(0);

        assertThrows(ConflictException.class, () -> partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY));
    }

    @Test
    void acceptOrder_Idempotent_WhenAlreadyAccepted() {
        partnerOrder.setStatus(PartnerOrderStatus.ACCEPTED);
        stubSelectForUpdate(PartnerOrderStatus.ACCEPTED, 0L);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        var response = partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID, IDEMPOTENCY_KEY);

        assertEquals(PartnerOrderStatus.ACCEPTED, response.status());
    }
}
