package com.yashmerino.ecommerce.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yashmerino.ecommerce.kafka.events.NotificationRequestedEvent;
import com.yashmerino.ecommerce.kafka.events.NotificationRequestedEventV2;
import com.yashmerino.ecommerce.model.Notification;
import com.yashmerino.ecommerce.model.NotificationContent;
import com.yashmerino.ecommerce.repository.NotificationRepository;
import com.yashmerino.ecommerce.service.InboxService;
import com.yashmerino.ecommerce.service.NotificationSender;
import com.yashmerino.ecommerce.service.NotificationSenderFactory;
import com.yashmerino.ecommerce.service.NotificationService;
import com.yashmerino.ecommerce.service.NotificationTemplate;
import com.yashmerino.ecommerce.utils.ContactType;
import com.yashmerino.ecommerce.utils.NotificationStatus;
import com.yashmerino.ecommerce.utils.NotificationType;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

/**
 * Payment service implementation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    /**
     * Notification repository.
     */
    private final NotificationRepository notificationRepository;

    /**
     * Notification sender factory.
     */
    private final NotificationSenderFactory senderFactory;

    /**
     * Notifications' templates.
     */
    private final Map<NotificationType, NotificationTemplate> templates;

    /**
     * Inbox service for deduplication.
     */
    private final InboxService inboxService;

    /**
     * Object mapper for JSON conversion.
     */
    private final ObjectMapper objectMapper;

    /**
     * Sends the notification.
     *
     * @param event is the event from Kafka topic.
     */
    @Override
    @Transactional
    public void sendNotification(NotificationRequestedEvent event) {
        Notification notification = null;
        try {
            notification = Notification.builder()
                    .notificationType(event.notificationType())
                    .contact(event.contact())
                    .contactType(event.contactType())
                    .status(NotificationStatus.PENDING)
                    .retryCount(0)
                    .payload(new ObjectMapper().writeValueAsString(event.payload()))
                    .build();
        } catch (JsonProcessingException e) {
            log.error("Notification payload couldn't be converted to JSON.", e);
            return;
        }

        notificationRepository.save(notification);

        try {
            sendNotificationWithRetry(notification, event);
        } catch (Exception e) {
            log.error("Notification failed after all retry attempts", e);
        }
    }

    /**
     * Sends notification with automatic retry on failure.
     * Uses Spring Retry for exponential backoff (1s, 2s, 4s).
     *
     * @param notification the notification entity
     * @param event the notification event
     */
    @Retryable(
        retryFor = Exception.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 1000, multiplier = 2),
        listeners = {"retryListener"}
    )
    private void sendNotificationWithRetry(Notification notification, NotificationRequestedEvent event) {
        notification.setRetryCount(notification.getRetryCount() + 1);
        
        log.info("Attempting to send notification (attempt {})", notification.getRetryCount());

        NotificationTemplate template = templates.get(event.notificationType());
        NotificationContent content = template.build(event.payload());

        NotificationSender sender = senderFactory.getSender(event.contactType().toString());
        sender.send(event.contact(), content);

        notification.setStatus(NotificationStatus.SENT);
        notification.setSentAt(LocalDateTime.ofInstant(Instant.now(), ZoneId.systemDefault()));
        notificationRepository.save(notification);
        
        log.info("Notification sent successfully after {} attempts", notification.getRetryCount());
    }

    /**
     * Recovery method called after all retry attempts fail.
     *
     * @param e the exception that caused the failure
     * @param notification the notification entity
     * @param event the notification event
     */
    @Recover
    private void handleNotificationFailure(Exception e, Notification notification, NotificationRequestedEvent event) {
        log.error("Notification failed after {} attempts: {}", notification.getRetryCount(), e.getMessage());
        
        notification.setStatus(NotificationStatus.FAILED);
        notification.setLastError(e.getMessage());
        notificationRepository.save(notification);
    }

    @Override
    @Transactional
    public void sendNotificationV2(NotificationRequestedEventV2 event) {
        if (inboxService.isAlreadyProcessed("notification-service", event.eventId())) {
            log.info("Already processed notification event: {}", event.eventId());
            return;
        }

        Notification notification = new Notification();
        notification.setContact(event.contact());
        notification.setContactType(ContactType.valueOf(event.contactType()));
        notification.setNotificationType(NotificationType.valueOf(event.notificationType()));
        notification.setPayload(convertPayloadToString(event.payload()));
        notification.setStatus(NotificationStatus.PENDING);
        notification.setEventId(event.eventId());
        notification.setBusinessIdempotencyKey(event.idempotencyKey());
        notification = notificationRepository.save(notification);

        try {
            NotificationContent content = buildContent(event.notificationType(), event.payload());
            NotificationSender sender = senderFactory.getSender(event.contactType());
            sender.send(event.contact(), content);

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notificationRepository.save(notification);

            inboxService.markProcessed("notification-service", event.eventId(),
                "NotificationRequestedEventV2", event.correlationId(), event.aggregateId());
        } catch (Exception e) {
            log.error("Failed to send notification: {}", e.getMessage(), e);
            notification.setStatus(NotificationStatus.FAILED);
            notification.setLastError(e.getMessage());
            notificationRepository.save(notification);
        }
    }

    private String convertPayloadToString(Map<String, Object> payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private NotificationContent buildContent(String notificationType, Map<String, Object> payload) {
        NotificationType type = NotificationType.valueOf(notificationType);
        NotificationTemplate template = templates.get(type);
        return template.build(payload);
    }
}
