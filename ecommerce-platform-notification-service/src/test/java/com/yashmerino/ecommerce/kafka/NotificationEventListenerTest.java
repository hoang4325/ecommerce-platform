package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.NotificationRequestedEvent;
import com.yashmerino.ecommerce.kafka.events.NotificationRequestedEventV2;
import com.yashmerino.ecommerce.service.NotificationService;
import com.yashmerino.ecommerce.utils.ContactType;
import com.yashmerino.ecommerce.utils.NotificationType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test class for NotificationEventListener.
 */
@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private NotificationEventListener listener;

    private NotificationRequestedEvent testEvent;
    private Map<String, Object> payload;

    @BeforeEach
    void setUp() {
        payload = new HashMap<>();
        payload.put("amount", 100.0);
        payload.put("orderId", 1);
        payload.put("paymentId", 123);

        testEvent = new NotificationRequestedEvent(
                NotificationType.PAYMENT_SUCCESS,
                ContactType.EMAIL,
                "test@example.com",
                payload
        );
    }

    @Test
    void testOnNotificationRequestedSuccess() {
        doNothing().when(notificationService).sendNotification(any(NotificationRequestedEvent.class));

        listener.onNotificationRequested(testEvent);

        verify(notificationService, times(1)).sendNotification(testEvent);
    }

    @Test
    void testOnNotificationRequestedHandlesException() {
        doThrow(new RuntimeException("Service error"))
                .when(notificationService)
                .sendNotification(any(NotificationRequestedEvent.class));

        assertDoesNotThrow(() -> listener.onNotificationRequested(testEvent));
        
        verify(notificationService, times(1)).sendNotification(testEvent);
    }

    @Test
    void testOnNotificationRequestedWithDifferentEventTypes() {
        NotificationRequestedEvent userRegisteredEvent = new NotificationRequestedEvent(
                NotificationType.USER_REGISTERED,
                ContactType.EMAIL,
                "newuser@example.com",
                Map.of("username", "newuser")
        );

        doNothing().when(notificationService).sendNotification(any(NotificationRequestedEvent.class));

        listener.onNotificationRequested(userRegisteredEvent);

        verify(notificationService, times(1)).sendNotification(userRegisteredEvent);
    }

    @Test
    void testOnNotificationRequestedWithSmsContactType() {
        NotificationRequestedEvent smsEvent = new NotificationRequestedEvent(
                NotificationType.PAYMENT_FAILED,
                ContactType.SMS,
                "+1234567890",
                payload
        );

        doNothing().when(notificationService).sendNotification(any(NotificationRequestedEvent.class));

        listener.onNotificationRequested(smsEvent);

        verify(notificationService, times(1)).sendNotification(smsEvent);
    }

    @Test
    void testOnNotificationRequestedMultipleInvocations() {
        doNothing().when(notificationService).sendNotification(any(NotificationRequestedEvent.class));

        listener.onNotificationRequested(testEvent);
        listener.onNotificationRequested(testEvent);
        listener.onNotificationRequested(testEvent);

        // Assert
        verify(notificationService, times(3)).sendNotification(testEvent);
    }

    @Test
    void testOnNotificationRequestedV2DelegatesToService() {
        NotificationRequestedEventV2 v2Event = new NotificationRequestedEventV2(
                "event-1", "ORDER_CREATED", 2, "2026-07-11T10:00:00",
                "corr-1", 123L, "order-service", "idem-1",
                "PAYMENT_SUCCESS", "EMAIL", "test@example.com", Map.of()
        );
        doNothing().when(notificationService).sendNotificationV2(any(NotificationRequestedEventV2.class));

        listener.onNotificationRequestedV2(v2Event);

        verify(notificationService, times(1)).sendNotificationV2(v2Event);
    }

    @Test
    void testOnNotificationRequestedV2ThrowsOnError() {
        NotificationRequestedEventV2 v2Event = new NotificationRequestedEventV2(
                "event-1", "ORDER_CREATED", 2, "2026-07-11T10:00:00",
                "corr-1", 123L, "order-service", "idem-1",
                "PAYMENT_SUCCESS", "EMAIL", "test@example.com", Map.of()
        );
        doThrow(new RuntimeException("V2 error"))
                .when(notificationService).sendNotificationV2(any(NotificationRequestedEventV2.class));

        assertThrows(RuntimeException.class, () -> listener.onNotificationRequestedV2(v2Event));

        verify(notificationService, times(1)).sendNotificationV2(v2Event);
    }
}
