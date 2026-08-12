package site.yesaido.notification_server.rabbitmq.refactor.command;

import java.time.OffsetDateTime;
import java.util.UUID;

public record RabbitMqNotificationCommand(
        UUID eventId,
        String eventCode,
        String targetType,
        Long targetId,
        OffsetDateTime occurredAt,
        Object payload
) {
}
