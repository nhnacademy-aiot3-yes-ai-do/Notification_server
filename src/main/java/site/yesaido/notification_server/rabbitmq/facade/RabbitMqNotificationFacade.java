package site.yesaido.notification_server.rabbitmq.facade;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationDeliveryPersistenceService;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationPersistenceService;
import site.yesaido.notification_server.rabbitmq.processor.AiNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.CultivationNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.RuleEngineNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.UserNotificationProcessor;
import site.yesaido.notification_server.service.DeliveryDispatchService;

@Service
@RequiredArgsConstructor
public class RabbitMqNotificationFacade {

    private final RabbitMqNotificationPersistenceService persistenceService;
    private final RabbitMqNotificationDeliveryPersistenceService deliveryPersistenceService;
    private final DeliveryDispatchService dispatchService;
    private final RuleEngineNotificationProcessor ruleEngineProcessor;
    private final AiNotificationProcessor aiProcessor;
    private final CultivationNotificationProcessor cultivationProcessor;
    private final UserNotificationProcessor userProcessor;

    // Rule Engine
    public void handle(RuleEngineEvent.ThresholdStatusChangedEvent event) {
        RabbitMqNotificationCommand process = ruleEngineProcessor.process(event);
        persist(process);
    }

    public void handle(RuleEngineEvent.AutomationStateChangedEvent event) {
        RabbitMqNotificationCommand process = ruleEngineProcessor.process(event);
        persist(process);
    }

    // AI
    public void handle(AiEvent.DailyFeedbackGeneratedEvent event) {
        RabbitMqNotificationCommand process = aiProcessor.process(event);
        persist(process);
    }

    public void handle(AiEvent.CultivationCompletedEvent event) {
        RabbitMqNotificationCommand process = aiProcessor.process(event);
        persist(process);
    }

    // Cultivation
    public void handle(CultivationEvent.HarvestCompletedEvent event) {
        RabbitMqNotificationCommand process = cultivationProcessor.process(event);
        persist(process);
    }

    public void handle(CultivationEvent.SensorDataUnavailableEvent event) {
        RabbitMqNotificationCommand process = cultivationProcessor.process(event);
        persist(process);
    }

    public void handle(CultivationEvent.CultivationMemberInvitedEvent event) {
        RabbitMqNotificationCommand process = cultivationProcessor.process(event);
        persist(process);
    }

    // User
    public void handle(UserEvent.UserLoginAttemptedEvent event) {
        RabbitMqNotificationCommand process = userProcessor.process(event);
        persist(process);
    }

    public void handle(UserEvent.UserPasswordChangeAttemptedEvent event) {
        RabbitMqNotificationCommand process = userProcessor.process(event);
        persist(process);
    }

    public void handle(UserEvent.UserAccountReactivationAttemptedEvent event) {
        RabbitMqNotificationCommand process = userProcessor.process(event);
        persist(process);
    }

    public void handle(UserEvent.InquirySubmittedEvent event) {
        RabbitMqNotificationCommand process = userProcessor.process(event);
        persist(process);
    }

    // 공통
    private void persist(site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand command) {
        persistenceService.persist(command);
        deliveryPersistenceService.activateForDispatch(command.eventId())
                .forEach(dispatchService::dispatch);
    }
}
