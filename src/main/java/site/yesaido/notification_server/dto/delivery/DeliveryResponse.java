package site.yesaido.notification_server.dto.delivery;

import java.time.LocalDateTime;
import site.yesaido.notification_server.entity.DeliveryStatus;
import site.yesaido.notification_server.entity.NotificationDelivery;

public record DeliveryResponse(
        Long id,
        Long notificationId,
        Long subscriptionId,
        String channelCode,
        String message,
        DeliveryStatus status,
        short attemptCount,
        LocalDateTime sentAt,
        LocalDateTime createdAt
) {

    public static DeliveryResponse from(NotificationDelivery delivery) {
        return new DeliveryResponse(
                delivery.getId(),
                delivery.getNotification().getId(),
                delivery.getSubscription().getId(),
                delivery.getSubscription().getEndpoint().getChannelType().getCode(),
                delivery.getRenderedMessage(),
                delivery.getStatus(),
                delivery.getAttemptCount(),
                delivery.getSentAt(),
                delivery.getCreatedAt());
    }
}
