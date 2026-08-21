package site.yesaido.notification_server.rabbitmq.processor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.contract.NotificationEventDefinition;

@Component
public class AiNotificationProcessor {

    private static final String UNAVAILABLE = "미제공";

    public RabbitMqNotificationCommand process(AiEvent.DailyFeedbackGeneratedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.DAILY_FEEDBACK_COMPLETED,
                event.cultivationId(), event.occurredAt(), Map.of(
                        "cultivationName", valueOrUnavailable(event.cultivationName()),
                        "feedbackSummary", valueOrUnavailable(event.feedbackContent())));
    }

    public RabbitMqNotificationCommand process(AiEvent.CultivationCompletedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.CULTIVATION_FINISHED,
                event.cultivationId(), event.occurredAt(), Map.of(
                        "cultivationName", valueOrUnavailable(event.cultivationName())));
    }

    private RabbitMqNotificationCommand command(UUID eventId, NotificationEventDefinition definition,
                                                 long targetId, OffsetDateTime occurredAt, Map<String, Object> payload) {
        return new RabbitMqNotificationCommand(eventId, definition.code(), definition.targetType(), targetId, occurredAt, payload);
    }

    private String valueOrUnavailable(String value) {
        return value == null || value.isBlank() ? UNAVAILABLE : value;
    }
}
