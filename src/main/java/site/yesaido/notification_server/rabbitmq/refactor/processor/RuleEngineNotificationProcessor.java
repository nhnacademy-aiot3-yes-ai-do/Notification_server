package site.yesaido.notification_server.rabbitmq.refactor.processor;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.refactor.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.refactor.contract.NotificationEventDefinition;

@Component
public class RuleEngineNotificationProcessor {

    public RabbitMqNotificationCommand process(RuleEngineEvent.ThresholdStatusChangedEvent event) {
        NotificationEventDefinition definition = event.status() == RuleEngineEvent.ThresholdStatus.EXCEEDED
                ? NotificationEventDefinition.ENVIRONMENT_THRESHOLD_BREACHED
                : NotificationEventDefinition.ENVIRONMENT_RECOVERED;
        return command(event.eventId(), definition, event.sensorData().cultivationId(), event.occurredAt(), event);
    }

    public RabbitMqNotificationCommand process(RuleEngineEvent.AutomationStateChangedEvent event) {
        return command(event.eventId(), NotificationEventDefinition.ACTUATOR_CONTROL_FAILED,
                event.cultivationId(), event.occurredAt(), event);
    }

    private RabbitMqNotificationCommand command(UUID eventId, NotificationEventDefinition definition,
                                                 long targetId, OffsetDateTime occurredAt, Object payload) {
        return new RabbitMqNotificationCommand(eventId, definition.code(), definition.targetType(), targetId, occurredAt, payload);
    }
}
