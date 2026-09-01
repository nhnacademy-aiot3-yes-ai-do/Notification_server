package site.yesaido.notification_server.dto.admin;

import site.yesaido.notification_server.entity.NotificationEventType;

public record NotificationEventTypeResponse(
        Long id,
        String code,
        String displayName,
        String description,
        String targetType
) {
    public static NotificationEventTypeResponse from(NotificationEventType eventType) {
        return new NotificationEventTypeResponse(
                eventType.getId(),
                eventType.getCode(),
                eventType.getDisplayName(),
                eventType.getDescription(),
                eventType.getTargetType().getTargetType());
    }
}
