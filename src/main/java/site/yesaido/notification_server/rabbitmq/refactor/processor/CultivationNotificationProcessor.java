package site.yesaido.notification_server.rabbitmq.refactor.processor;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.refactor.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.refactor.contract.NotificationEventDefinition;

@Component
public class CultivationNotificationProcessor {

    public RabbitMqNotificationCommand process(CultivationEvent.HarvestCompletedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.HARVEST_COMPLETED,
                event.cultivationId(), event.harvestedAt(), event);
    }

    public RabbitMqNotificationCommand process(CultivationEvent.SensorDataUnavailableEvent event) {
        return command(event.eventId(), NotificationEventDefinition.SENSOR_OFFLINE,
                event.cultivationId(), event.occurredAt(), event);
    }

    public RabbitMqNotificationCommand process(CultivationEvent.CultivationMemberInvitedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.CULTIVATION_MEMBER_INVITED,
                event.cultivationId(), event.occurredAt(), event);
    }

    private RabbitMqNotificationCommand command(UUID eventId, NotificationEventDefinition definition,
                                                 long targetId, OffsetDateTime occurredAt, Object payload) {
        return new RabbitMqNotificationCommand(eventId, definition.code(), definition.targetType(), targetId, occurredAt, payload);
    }
}
