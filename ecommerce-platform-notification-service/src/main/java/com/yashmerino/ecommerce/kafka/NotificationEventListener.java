package com.yashmerino.ecommerce.kafka;

import com.yashmerino.ecommerce.kafka.events.NotificationRequestedEvent;
import com.yashmerino.ecommerce.kafka.events.NotificationRequestedEventV2;
import com.yashmerino.ecommerce.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Kafka notification events listener.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationEventListener {

    private final NotificationService notificationService;

    @KafkaListener(
        topics = "notification.requested",
        groupId = "notification-service"
    )
    public void onNotificationRequested(NotificationRequestedEvent event) {
        try {
            notificationService.sendNotification(event);
        } catch (Exception e) {
            log.error("Notification couldn't be processed.", e);
        }
    }

    @KafkaListener(
        topics = "${notification.topics.notification-requested-v2}",
        groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onNotificationRequestedV2(NotificationRequestedEventV2 event) {
        log.info("Received V2 notification request: type={}, contact={}",
            event.notificationType(), event.contact());
        try {
            notificationService.sendNotificationV2(event);
        } catch (Exception e) {
            log.error("Error processing V2 notification: {}", e.getMessage(), e);
            throw e;
        }
    }
}
