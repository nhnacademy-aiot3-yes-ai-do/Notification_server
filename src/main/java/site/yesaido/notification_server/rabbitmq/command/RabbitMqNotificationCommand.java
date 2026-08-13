package site.yesaido.notification_server.rabbitmq.command;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

public record RabbitMqNotificationCommand(
        UUID eventId,
        String eventCode,
        String targetType,
        Long targetId,
        OffsetDateTime occurredAt,
        Object payload,
        List<Long> recipientUserIds
) {
    public RabbitMqNotificationCommand {
        recipientUserIds = recipientUserIds == null ? List.of() : List.copyOf(recipientUserIds);
    }

    public RabbitMqNotificationCommand(
            UUID eventId,
            String eventCode,
            String targetType,
            Long targetId,
            OffsetDateTime occurredAt,
            Object payload
    ) {
        this(eventId, eventCode, targetType, targetId, occurredAt, payload, List.of());
    }
}
