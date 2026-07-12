package com.yashmerino.ecommerce.services;

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
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
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

    @InjectMocks
    private PartnerOrderServiceImpl partnerOrderService;

    private static final Long PARTNER_ID = 1L;
    private static final Long ORDER_ID = 100L;

    private Partner partner;
    private Order order;
    private PartnerOrder partnerOrder;

    @BeforeEach
    void setUp() {
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
    }

    @Test
    void acceptOrder_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.NEW);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID);

        assertEquals(PartnerOrderStatus.ACCEPTED, response.status());
        assertNotNull(partnerOrder.getAcceptedAt());
        verify(partnerOrderRepository).save(partnerOrder);
    }

    @Test
    void acceptOrder_WrongStatus_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.SHIPPED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class, () -> partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID));
    }

    @Test
    void acceptOrder_NotFound_ThrowsEntityNotFound() {
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> partnerOrderService.acceptOrder(PARTNER_ID, ORDER_ID));
    }

    @Test
    void rejectOrder_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.NEW);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.rejectOrder(PARTNER_ID, ORDER_ID, "Out of stock");

        assertEquals(PartnerOrderStatus.REJECTED, response.status());
        assertNotNull(partnerOrder.getRejectedAt());
        assertEquals("Out of stock", partnerOrder.getRejectionReason());
    }

    @Test
    void rejectOrder_WrongStatus_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.ACCEPTED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.rejectOrder(PARTNER_ID, ORDER_ID, "reason"));
    }

    @Test
    void markPacking_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.ACCEPTED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.markPacking(PARTNER_ID, ORDER_ID);

        assertEquals(PartnerOrderStatus.PACKING, response.status());
        assertNotNull(partnerOrder.getPackedAt());
    }

    @Test
    void markPacking_WrongStatus_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.NEW);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class, () -> partnerOrderService.markPacking(PARTNER_ID, ORDER_ID));
    }

    @Test
    void markReadyToShip_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.PACKING);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.markReadyToShip(PARTNER_ID, ORDER_ID);

        assertEquals(PartnerOrderStatus.READY_TO_SHIP, response.status());
        assertNotNull(partnerOrder.getReadyToShipAt());
    }

    @Test
    void markReadyToShip_WrongStatus_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.ACCEPTED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.markReadyToShip(PARTNER_ID, ORDER_ID));
    }

    @Test
    void shipOrder_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.READY_TO_SHIP);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.shipOrder(PARTNER_ID, ORDER_ID);

        assertEquals(PartnerOrderStatus.SHIPPED, response.status());
        assertNotNull(partnerOrder.getShippedAt());
    }

    @Test
    void shipOrder_WrongStatus_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.PACKING);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class, () -> partnerOrderService.shipOrder(PARTNER_ID, ORDER_ID));
    }

    @Test
    void deliverOrder_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.SHIPPED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.deliverOrder(PARTNER_ID, ORDER_ID);

        assertEquals(PartnerOrderStatus.DELIVERED, response.status());
        assertNotNull(partnerOrder.getDeliveredAt());
    }

    @Test
    void deliverOrder_WrongStatus_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.READY_TO_SHIP);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class, () -> partnerOrderService.deliverOrder(PARTNER_ID, ORDER_ID));
    }

    @Test
    void cancelOrder_FromNew_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.NEW);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.cancelOrder(PARTNER_ID, ORDER_ID, "Customer request");

        assertEquals(PartnerOrderStatus.CANCELLED, response.status());
        assertNotNull(partnerOrder.getCancelledAt());
        assertEquals("Customer request", partnerOrder.getCancelReason());
    }

    @Test
    void cancelOrder_FromAccepted_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.ACCEPTED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.cancelOrder(PARTNER_ID, ORDER_ID, "Supplier issue");

        assertEquals(PartnerOrderStatus.CANCELLED, response.status());
    }

    @Test
    void cancelOrder_Shipped_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.SHIPPED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.cancelOrder(PARTNER_ID, ORDER_ID, "reason"));
    }

    @Test
    void requestReturn_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.DELIVERED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.requestReturn(PARTNER_ID, ORDER_ID, "Defective item");

        assertEquals(PartnerOrderStatus.RETURN_REQUESTED, response.status());
        assertEquals("Defective item", partnerOrder.getCancelReason());
    }

    @Test
    void requestReturn_WrongStatus_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.SHIPPED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class,
                () -> partnerOrderService.requestReturn(PARTNER_ID, ORDER_ID, "reason"));
    }

    @Test
    void approveReturn_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.RETURN_REQUESTED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));
        lenient().when(partnerOrderRepository.save(any(PartnerOrder.class))).thenReturn(partnerOrder);

        var response = partnerOrderService.approveReturn(PARTNER_ID, ORDER_ID);

        assertEquals(PartnerOrderStatus.RETURNED, response.status());
    }

    @Test
    void approveReturn_WrongStatus_ThrowsInvalidInput() {
        partnerOrder.setStatus(PartnerOrderStatus.DELIVERED);
        lenient().doNothing().when(authz).requireOrderFulfillment(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        assertThrows(InvalidInputException.class, () -> partnerOrderService.approveReturn(PARTNER_ID, ORDER_ID));
    }

    @Test
    void getPartnerOrders_Success() {
        lenient().doNothing().when(authz).requireOrderRead(PARTNER_ID);
        PageRequest pageable = PageRequest.of(0, 10);
        Page<PartnerOrder> orderPage = new PageImpl<>(List.of(partnerOrder));
        lenient().when(partnerOrderRepository.findByPartnerId(PARTNER_ID, pageable)).thenReturn(orderPage);

        Page<PartnerOrderResponse> response = partnerOrderService.getPartnerOrders(PARTNER_ID, pageable);

        assertNotNull(response);
        assertEquals(1, response.getTotalElements());
        verify(authz).requireOrderRead(PARTNER_ID);
    }

    @Test
    void getPartnerOrder_Success() {
        partnerOrder.setStatus(PartnerOrderStatus.NEW);
        lenient().doNothing().when(authz).requireOrderRead(PARTNER_ID);
        lenient().when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID))
                .thenReturn(Optional.of(partnerOrder));

        var response = partnerOrderService.getPartnerOrder(PARTNER_ID, ORDER_ID);

        assertNotNull(response);
        assertEquals(ORDER_ID, response.id());
        assertEquals(PartnerOrderStatus.NEW, response.status());
    }

    @Test
    void getPartnerOrder_NotFound_ThrowsEntityNotFound() {
        lenient().doNothing().when(authz).requireOrderRead(PARTNER_ID);
        when(partnerOrderRepository.findByIdAndPartnerId(ORDER_ID, PARTNER_ID)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class,
                () -> partnerOrderService.getPartnerOrder(PARTNER_ID, ORDER_ID));
    }
}
