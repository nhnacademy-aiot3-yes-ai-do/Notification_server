package site.yesaido.notification_server.rabbitmq.processor;

import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.event.HarvestCompletedPayload;
import site.yesaido.notification_server.rabbitmq.event.MemberAddedPayload;
import site.yesaido.notification_server.rabbitmq.event.NotificationEnvelope;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.contract.NotificationEventDefinition;
import site.yesaido.notification_server.rabbitmq.exception.RabbitMqHarvestQuantityMissingException;

import java.time.OffsetDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class CultivationNotificationProcessor {

    private static final String UNAVAILABLE = "미제공";
    private static final String CULTIVATION_NAME = "cultivationName";

    public RabbitMqNotificationCommand processHarvestCompleted(
            NotificationEnvelope<HarvestCompletedPayload> envelope) {
        requireTargetId(envelope);
        HarvestCompletedPayload payload = envelope.payload();
        if (payload == null || payload.harvestWeight() == null) {
            throw new RabbitMqHarvestQuantityMissingException(envelope.targetId());
        }
        return command(envelope.eventUuid(), NotificationEventDefinition.HARVEST_COMPLETED,
                envelope.targetId(), envelope.occurredAtOffsetDateTime(), Map.of(
                        CULTIVATION_NAME, valueOrUnavailable(payload.cultivationName()),
                        "harvestWeight", payload.harvestWeight()));
    }

    public RabbitMqNotificationCommand processMemberAdded(
            NotificationEnvelope<MemberAddedPayload> envelope) {
        requireTargetId(envelope);
        MemberAddedPayload payload = envelope.payload() == null
                ? new MemberAddedPayload(null, null, null)
                : envelope.payload();
        Map<String, Object> templatePayload = new LinkedHashMap<>();
        templatePayload.put("cultivationId",
                payload.cultivationId() == null ? UNAVAILABLE : payload.cultivationId());
        templatePayload.put(CULTIVATION_NAME, valueOrUnavailable(payload.cultivationName()));
        templatePayload.put("role", valueOrUnavailable(payload.role()));
        return command(envelope.eventUuid(), NotificationEventDefinition.MEMBER_ADDED,
                envelope.targetId(), envelope.occurredAtOffsetDateTime(), templatePayload);
    }

    public RabbitMqNotificationCommand process(CultivationEvent.HarvestCompletedEvent event) {
        if (event.harvestQuantity() == null) {
            throw new RabbitMqHarvestQuantityMissingException(event.cultivationId(), event.harvestId());
        }
        return command(event.eventId(), NotificationEventDefinition.HARVEST_COMPLETED,
                event.cultivationId(), event.harvestedAt(), Map.of(
                        CULTIVATION_NAME, valueOrUnavailable(event.cultivationName()),
                        "harvestWeight", event.harvestQuantity()));
    }

    public RabbitMqNotificationCommand process(CultivationEvent.SensorDataUnavailableEvent event) {
        return command(event.eventId(), NotificationEventDefinition.SENSOR_OFFLINE,
                event.cultivationId(), event.occurredAt(), Map.of(
                        CULTIVATION_NAME, UNAVAILABLE,
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

    private void requireTargetId(NotificationEnvelope<?> envelope) {
        if (envelope.targetId() == null) {
            throw new IllegalArgumentException("targetId");
        }
    }
}
