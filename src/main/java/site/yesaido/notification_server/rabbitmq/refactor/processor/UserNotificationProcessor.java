package site.yesaido.notification_server.rabbitmq.refactor.processor;

import java.time.OffsetDateTime;
import java.util.UUID;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;
import site.yesaido.notification_server.rabbitmq.refactor.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.refactor.contract.NotificationEventDefinition;

@Component
public class UserNotificationProcessor {

    public RabbitMqNotificationCommand process(UserEvent.UserLoginAttemptedEvent event) {
        return userCommand(event.eventId(), event.succeeded()
                ? NotificationEventDefinition.LOGIN_SUCCEEDED : NotificationEventDefinition.LOGIN_FAILED,
                event.userId(), event.occurredAt(), event);
    }

    public RabbitMqNotificationCommand process(UserEvent.UserPasswordChangeAttemptedEvent event) {
        return userCommand(event.eventId(), event.succeeded()
                ? NotificationEventDefinition.PASSWORD_CHANGED : NotificationEventDefinition.PASSWORD_CHANGE_FAILED,
                event.userId(), event.occurredAt(), event);
    }

    public RabbitMqNotificationCommand process(UserEvent.UserAccountReactivationAttemptedEvent event) {
        return userCommand(event.eventId(), event.succeeded()
                ? NotificationEventDefinition.ACCOUNT_REACTIVATED
                : NotificationEventDefinition.ACCOUNT_REACTIVATION_FAILED,
                event.userId(), event.occurredAt(), event);
    }

    public RabbitMqNotificationCommand process(UserEvent.InquirySubmittedEvent event) {
        NotificationEventDefinition definition = event.inquiryType() == UserEvent.InquiryType.ANSWER
                ? NotificationEventDefinition.INQUIRY_ANSWERED : NotificationEventDefinition.INQUIRY_SUBMITTED;
        return new RabbitMqNotificationCommand(event.eventId(), definition.code(), definition.targetType(),
                event.inquiryId(), event.occurredAt(), event);
    }

    private RabbitMqNotificationCommand userCommand(UUID eventId, NotificationEventDefinition definition,
                                                     long userId, OffsetDateTime occurredAt, Object payload) {
        return new RabbitMqNotificationCommand(eventId, definition.code(), definition.targetType(), userId, occurredAt, payload);
    }
}
