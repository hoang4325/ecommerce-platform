package com.yashmerino.ecommerce.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.CancelRequestDTO;
import com.yashmerino.ecommerce.repositories.OrderRepository;
import com.yashmerino.ecommerce.repositories.PaymentRepository;
import com.yashmerino.ecommerce.services.interfaces.CommissionService;
import com.yashmerino.ecommerce.services.interfaces.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CheckoutServiceTest {

    @Mock
    private JdbcTemplate jdbc;

    @Mock
    private OrderRepository orders;

    @Mock
    private PaymentRepository payments;

    @Mock
    private UserService users;

    @Mock
    private ObjectMapper objectMapper;

    @Mock
    private InboxService inboxService;

    @Mock
    private CommissionService commissionService;

    @Mock
    private OutboxService outboxService;

    @InjectMocks
    private CheckoutService checkoutService;

    private User user;
    private CancelRequestDTO request;
    private UUID idempotencyKey;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setId(1L);
        request = new CancelRequestDTO("test reason");
        idempotencyKey = UUID.randomUUID();

        SecurityContext securityContext = mock(SecurityContext.class);
        Authentication authentication = mock(Authentication.class);
        UserDetails userDetails = mock(UserDetails.class);

        SecurityContextHolder.setContext(securityContext);
        lenient().when(securityContext.getAuthentication()).thenReturn(authentication);
        lenient().when(authentication.getPrincipal()).thenReturn(userDetails);
        lenient().when(userDetails.getUsername()).thenReturn("testuser");
        lenient().when(users.getByUsername("testuser")).thenReturn(user);
    }

    @Test
    void cancelOrder_Success() {
        lenient().when(jdbc.query(anyString(), any(ResultSetExtractor.class), anyLong()))
                .thenReturn(new CheckoutService.CancelLock(1L, "CREATED", 1L, 1L, "AWAITING_PAYMENT_METHOD"))
                .thenReturn(null)
                .thenReturn(null)
                .thenReturn(null);
        lenient().when(jdbc.update(anyString(), anyLong())).thenReturn(1);

        assertDoesNotThrow(() -> checkoutService.cancelOrder(1L, idempotencyKey, request));

        verify(jdbc).update(startsWith("UPDATE payments SET status='CANCELLED'"), anyLong());
        verify(jdbc, atLeastOnce()).update(startsWith("UPDATE inventory_reservations SET status='EXPIRED'"), anyLong());
        verify(jdbc).update(startsWith("UPDATE orders SET status='CANCELLED'"), anyLong());
    }

    @Test
    void cancelOrder_AlreadyCancelled_ReturnsSilently() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), anyLong()))
                .thenReturn(new CheckoutService.CancelLock(1L, "CANCELLED", 1L, 1L, "CANCELLED"));

        assertDoesNotThrow(() -> checkoutService.cancelOrder(1L, idempotencyKey, request));

        verify(jdbc, never()).update(startsWith("UPDATE payments SET status='CANCELLED'"), anyLong());
    }

    @Test
    void cancelOrder_NotOwner_ThrowsAccessDenied() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), anyLong()))
                .thenReturn(new CheckoutService.CancelLock(1L, "CREATED", 2L, 1L, "AWAITING_PAYMENT_METHOD"));

        assertThrows(AccessDeniedException.class, () -> checkoutService.cancelOrder(1L, idempotencyKey, request));
    }

    @Test
    void cancelOrder_WrongState_ThrowsConflict() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), anyLong()))
                .thenReturn(new CheckoutService.CancelLock(1L, "PAID", 1L, 1L, "SUCCEEDED"));

        assertThrows(ConflictException.class, () -> checkoutService.cancelOrder(1L, idempotencyKey, request));
    }

    @Test
    void cancelOrder_PaymentNotAwaiting_ThrowsConflict() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), anyLong()))
                .thenReturn(new CheckoutService.CancelLock(1L, "CREATED", 1L, 1L, "PENDING"));

        assertThrows(ConflictException.class, () -> checkoutService.cancelOrder(1L, idempotencyKey, request));
    }

    @Test
    void cancelOrder_OrderNotFound_ThrowsException() {
        when(jdbc.query(anyString(), any(ResultSetExtractor.class), anyLong())).thenReturn(null);

        assertThrows(jakarta.persistence.EntityNotFoundException.class,
                () -> checkoutService.cancelOrder(1L, idempotencyKey, request));
    }

    @Test
    void createPartnerOrdersAtCheckout_UsesValidPartnerOrderInsertShape() throws Exception {
        when(jdbc.query(startsWith("SELECT oi.product_id"), any(RowMapper.class), eq(10L)))
                .thenReturn(List.of(new CommissionService.CommissionRequest(
                        20L, 30L, java.util.Set.of(), 40L, new BigDecimal("100.00"), "USD")));
        when(commissionService.resolveOrderItemCommissions(anyList()))
                .thenReturn(List.of(new CommissionService.CommissionResult(
                        20L, 30L, 1L, new BigDecimal("0.1000"), BigDecimal.ZERO,
                        new BigDecimal("10.00"), new BigDecimal("90.00"))));
        doReturn(1).when(jdbc).update(startsWith("UPDATE order_items SET"),
                any(), any(), any(), any(), any(), any(), any(), any());
        when(jdbc.query(startsWith("SELECT partner_id"), any(ResultSetExtractor.class), eq(10L)))
                .thenAnswer(invocation -> {
                    @SuppressWarnings("unchecked")
                    ResultSetExtractor<Object> extractor = invocation.getArgument(1);
                    ResultSet rs = mock(ResultSet.class);
                    when(rs.next()).thenReturn(true, false);
                    when(rs.getLong("partner_id")).thenReturn(40L);
                    when(rs.getBigDecimal("subtotal")).thenReturn(new BigDecimal("100.00"));
                    when(rs.getBigDecimal("discount_allocation")).thenReturn(BigDecimal.ZERO);
                    when(rs.getBigDecimal("total_commission")).thenReturn(new BigDecimal("10.00"));
                    when(rs.getBigDecimal("total_payable")).thenReturn(new BigDecimal("90.00"));
                    when(rs.getString("currency")).thenReturn("USD");
                    return extractor.extractData(rs);
                });

        var method = CheckoutService.class.getDeclaredMethod("createPartnerOrdersAtCheckout", Long.class);
        method.setAccessible(true);
        method.invoke(checkoutService, 10L);

        verify(jdbc).update(
                eq("INSERT INTO partner_orders(order_id,partner_id,status,subtotal,discount_allocation,shipping_allocation,commission_amount,partner_payable_amount,currency,settlement_status,version,created_at,updated_at) " +
                        "VALUES (?,?,'AWAITING_PAYMENT',?,?,0,?,?,?,'UNSETTLED',0,NOW(),NOW())"),
                eq(10L), eq(40L), eq(new BigDecimal("100.00")), eq(BigDecimal.ZERO),
                eq(new BigDecimal("10.00")), eq(new BigDecimal("90.00")), eq("USD"));
    }
}
