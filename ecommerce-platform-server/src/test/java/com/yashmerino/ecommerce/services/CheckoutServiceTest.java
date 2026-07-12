package com.yashmerino.ecommerce.services;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.exceptions.ConflictException;
import com.yashmerino.ecommerce.model.User;
import com.yashmerino.ecommerce.model.dto.CancelRequestDTO;
import com.yashmerino.ecommerce.repositories.OrderRepository;
import com.yashmerino.ecommerce.repositories.PaymentRepository;
import com.yashmerino.ecommerce.services.interfaces.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.ResultSetExtractor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;

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
}
