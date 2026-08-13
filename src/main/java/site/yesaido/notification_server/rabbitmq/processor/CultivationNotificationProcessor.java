package site.yesaido.notification_server.rabbitmq.processor;

import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.contract.NotificationEventDefinition;
import site.yesaido.notification_server.rabbitmq.exception.RabbitMqHarvestQuantityMissingException;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;

@Component
public class CultivationNotificationProcessor {

    private static final String UNAVAILABLE = "미제공";

    public RabbitMqNotificationCommand process(CultivationEvent.HarvestCompletedEvent event) {
        if (event.harvestQuantity() == null) {
            throw new RabbitMqHarvestQuantityMissingException(event.cultivationId(), event.harvestId());
        }
        return command(event.eventId(), NotificationEventDefinition.HARVEST_COMPLETED,
                event.cultivationId(), event.harvestedAt(), Map.of(
                        "cultivationName", valueOrUnavailable(event.cultivationName()),
                        "harvestWeight", event.harvestQuantity()));
    }

    public RabbitMqNotificationCommand process(CultivationEvent.SensorDataUnavailableEvent event) {
        return command(event.eventId(), NotificationEventDefinition.SENSOR_OFFLINE,
                event.cultivationId(), event.occurredAt(), Map.of(
                        "cultivationName", UNAVAILABLE,
                        "deviceName", valueOrUnavailable(event.deviceName())));
    }

    public RabbitMqNotificationCommand process(CultivationEvent.CultivationMemberInvitedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.CULTIVATION_MEMBER_INVITED,
                event.cultivationId(), event.occurredAt(), Map.of(
                        "inviterNickname", valueOrUnavailable(event.inviterNickname()),
                        "inviteeNickname", valueOrUnavailable(event.inviteeNickname()),
                        "invitationUrl", valueOrUnavailable(event.invitationUrl())));
    }

    private RabbitMqNotificationCommand command(UUID eventId, NotificationEventDefinition definition,
                                                 long targetId, OffsetDateTime occurredAt, Map<String, Object> payload) {
        return new RabbitMqNotificationCommand(eventId, definition.code(), definition.targetType(), targetId, occurredAt, payload);
    }

    private String valueOrUnavailable(String value) {
        return value == null || value.isBlank() ? UNAVAILABLE : value;
    }
}
