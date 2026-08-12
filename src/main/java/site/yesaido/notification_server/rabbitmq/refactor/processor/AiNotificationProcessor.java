package site.yesaido.notification_server.rabbitmq.refactor.processor;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.refactor.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.refactor.contract.NotificationEventDefinition;

@Component
public class AiNotificationProcessor {

    public RabbitMqNotificationCommand process(AiEvent.DailyFeedbackGeneratedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.DAILY_FEEDBACK_COMPLETED,
                event.cultivationId(), event.occurredAt(), event);
    }

    public RabbitMqNotificationCommand process(AiEvent.CultivationCompletedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.CULTIVATION_FINISHED,
                event.cultivationId(), event.occurredAt(), event);
    }

    private RabbitMqNotificationCommand command(UUID eventId, NotificationEventDefinition definition,
                                                 long targetId, OffsetDateTime occurredAt, Object payload) {
        return new RabbitMqNotificationCommand(eventId, definition.code(), definition.targetType(), targetId, occurredAt, payload);
    }
}
