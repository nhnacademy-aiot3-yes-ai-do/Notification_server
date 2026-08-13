package site.yesaido.notification_server.rabbitmq.processor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.contract.NotificationEventDefinition;

@Component
public class RuleEngineNotificationProcessor {

    private static final String UNAVAILABLE = "미제공";

    public RabbitMqNotificationCommand process(RuleEngineEvent.ThresholdStatusChangedEvent event) {
        NotificationEventDefinition definition = event.status() == RuleEngineEvent.ThresholdStatus.EXCEEDED
                ? NotificationEventDefinition.ENVIRONMENT_THRESHOLD_BREACHED
                : NotificationEventDefinition.ENVIRONMENT_RECOVERED;
        RuleEngineEvent.SensorDataDto sensor = event.sensorData();
        return command(event.eventId(), definition, sensor.cultivationId(), event.occurredAt(), Map.of(
                "cultivationName", valueOrUnavailable(sensor.place()),
                "sensorType", valueOrUnavailable(sensor.sensorType()),
                "currentValue", sensor.value(),
                "unit", valueOrUnavailable(sensor.unit()),
                // 현 RuleEngine 계약에는 임계 범위가 없다. 템플릿 렌더링은 보장하되 임의 수치를 만들지 않는다.
                "thresholdMin", UNAVAILABLE,
                "thresholdMax", UNAVAILABLE));
    }

    public RabbitMqNotificationCommand process(RuleEngineEvent.AutomationStateChangedEvent event) {
        // AutomationStateChanged는 enabled 상태로 성공한 ON/OFF 변경을 표현한다. V8이 성공 이벤트/템플릿을 제공한다.
        return command(event.eventId(), NotificationEventDefinition.ACTUATOR_CONTROL_SUCCEEDED,
                event.cultivationId(), event.occurredAt(), Map.of(
                        "cultivationName", UNAVAILABLE,
                        "deviceName", valueOrUnavailable(event.actuatorType()),
                        "controlType", event.enabled() ? "ON" : "OFF"));
    }

    private RabbitMqNotificationCommand command(UUID eventId, NotificationEventDefinition definition,
                                                 long targetId, OffsetDateTime occurredAt, Map<String, Object> payload) {
        return new RabbitMqNotificationCommand(eventId, definition.code(), definition.targetType(), targetId, occurredAt, payload);
    }

    private String valueOrUnavailable(String value) {
        return value == null || value.isBlank() ? UNAVAILABLE : value;
    }
}
