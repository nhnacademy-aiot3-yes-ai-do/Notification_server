package site.yesaido.notification_server.rabbitmq.processor;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.UUID;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.contract.NotificationEventDefinition;
import site.yesaido.notification_server.rabbitmq.exception.RabbitMqInquiryRecipientMissingException;

@Component
public class UserNotificationProcessor {

    private static final String UNAVAILABLE = "미제공";

    public RabbitMqNotificationCommand process(UserEvent.UserLoginAttemptedEvent event) {
        return userCommand(event.eventId(), event.succeeded()
                ? NotificationEventDefinition.LOGIN_SUCCEEDED : NotificationEventDefinition.LOGIN_FAILED,
                event.userId(), event.occurredAt(), Map.of("provider", UNAVAILABLE));
    }

    public RabbitMqNotificationCommand process(UserEvent.UserPasswordChangeAttemptedEvent event) {
        return userCommand(event.eventId(), event.succeeded()
                ? NotificationEventDefinition.PASSWORD_CHANGED : NotificationEventDefinition.PASSWORD_CHANGE_FAILED,
                event.userId(), event.occurredAt(), Map.of("nickname", valueOrUnavailable(event.nickname())));
    }

    public RabbitMqNotificationCommand process(UserEvent.UserAccountReactivationAttemptedEvent event) {
        return userCommand(event.eventId(), event.succeeded()
                ? NotificationEventDefinition.ACCOUNT_REACTIVATED
                : NotificationEventDefinition.ACCOUNT_REACTIVATION_FAILED,
                event.userId(), event.occurredAt(), Map.of("nickname", valueOrUnavailable(event.nickname())));
    }

    public RabbitMqNotificationCommand process(UserEvent.InquirySubmittedEvent event) {
        if (event.receiveUserIds() == null || event.receiveUserIds().isEmpty()) {
            throw new RabbitMqInquiryRecipientMissingException(event.inquiryId());
        }
        NotificationEventDefinition definition = event.inquiryType() == UserEvent.InquiryType.ANSWER
                ? NotificationEventDefinition.INQUIRY_ANSWERED : NotificationEventDefinition.INQUIRY_SUBMITTED;
        return new RabbitMqNotificationCommand(event.eventId(), definition.code(), definition.targetType(),
                event.inquiryId(), event.occurredAt(), Map.of("inquiryTitle", valueOrUnavailable(event.title())),
                event.receiveUserIds());
    }

    private RabbitMqNotificationCommand userCommand(UUID eventId, NotificationEventDefinition definition,
                                                     long userId, OffsetDateTime occurredAt, Map<String, Object> payload) {
        return new RabbitMqNotificationCommand(eventId, definition.code(), definition.targetType(), userId, occurredAt, payload);
    }

    private String valueOrUnavailable(String value) {
        return value == null || value.isBlank() ? UNAVAILABLE : value;
    }
}
