package site.yesaido.notification_server.dto.subscription;

import site.yesaido.notification_server.domain.NotificationSubscriptionType;

public record SubscriptionTypeResponse(
        Long id,
        String name,
        String description,
        String eventType,
        String targetType
) {

    public static SubscriptionTypeResponse from(NotificationSubscriptionType type) {
        return new SubscriptionTypeResponse(
                type.getId(),
                type.getName(),
                type.getDescription(),
                type.getEventType().getCode(),
                type.getTargetType().getTargetType());
    }
}
