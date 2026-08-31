package site.yesaido.notification_server.rabbitmq.processor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.contract.NotificationEventDefinition;

@Component
public class AiNotificationProcessor {

    private static final String UNAVAILABLE = "미제공";

    private final String publicOrigin;

    public AiNotificationProcessor(
            @Value("${notification.public-origin:https://yes-nhn.site}") String publicOrigin
    ) {
        this.publicOrigin = trimTrailingSlash(publicOrigin);
    }

    public RabbitMqNotificationCommand process(AiEvent.DailyFeedbackGeneratedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.DAILY_FEEDBACK_COMPLETED,
                event.cultivationId(), event.occurredAt(), Map.of(
                        "cultivationName", valueOrUnavailable(event.cultivationName()),
                        "feedbackSummary", valueOrUnavailable(event.feedbackContent()),
                        "feedbackUrl", toPublicUrl(event.feedbackUrl())));
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

    private String toPublicUrl(String value) {
        if (value == null || value.isBlank()) {
            return UNAVAILABLE;
        }
        String trimmed = value.trim();
        if (trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        if (trimmed.startsWith("/")) {
            return publicOrigin + trimmed;
        }
        return publicOrigin + "/" + trimmed;
    }

    private static String trimTrailingSlash(String origin) {
        if (origin == null || origin.isBlank()) {
            return "https://yes-nhn.site";
        }
        String trimmed = origin.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.isEmpty() ? "https://yes-nhn.site" : trimmed;
    }
}
