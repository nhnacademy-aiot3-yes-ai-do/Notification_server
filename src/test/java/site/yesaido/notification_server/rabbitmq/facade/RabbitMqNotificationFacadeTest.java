package site.yesaido.notification_server.rabbitmq.facade;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.event.HarvestCompletedPayload;
import site.yesaido.notification_server.rabbitmq.event.MemberAddedPayload;
import site.yesaido.notification_server.rabbitmq.event.NotificationEnvelope;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationDeliveryPersistenceService;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationPersistenceService;
import site.yesaido.notification_server.rabbitmq.processor.AiNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.CultivationNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.RuleEngineNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.UserNotificationProcessor;
import site.yesaido.notification_server.service.DeliveryDispatchService;

class RabbitMqNotificationFacadeTest {

    @Test
    void 모든_이벤트종류를_담당_Processor로_변환해_저장하고_활성화한다() {
        RabbitMqNotificationPersistenceService persistenceService = mock(RabbitMqNotificationPersistenceService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryPersistenceService =
                mock(RabbitMqNotificationDeliveryPersistenceService.class);
        DeliveryDispatchService dispatchService = mock(DeliveryDispatchService.class);
        RuleEngineNotificationProcessor ruleEngineProcessor = mock(RuleEngineNotificationProcessor.class);
        AiNotificationProcessor aiProcessor = mock(AiNotificationProcessor.class);
        CultivationNotificationProcessor cultivationProcessor = mock(CultivationNotificationProcessor.class);
        UserNotificationProcessor userProcessor = mock(UserNotificationProcessor.class);
        RabbitMqNotificationFacade facade = new RabbitMqNotificationFacade(
                persistenceService,
                deliveryPersistenceService,
                dispatchService,
                ruleEngineProcessor,
                aiProcessor,
                cultivationProcessor,
                userProcessor);

        OffsetDateTime now = OffsetDateTime.now();
        RuleEngineEvent.AutomationStateChangedEvent automation =
                new RuleEngineEvent.AutomationStateChangedEvent(
                        UUID.randomUUID(), 3L, "FAN", "자동화 상태 변경", true, now);
        AiEvent.DailyFeedbackGeneratedEvent dailyFeedback =
                new AiEvent.DailyFeedbackGeneratedEvent(
                        UUID.randomUUID(), 20L, 3L, "테스트 재배지", "/feedback/1", "좋음", now);
        AiEvent.CultivationCompletedEvent cultivationCompleted =
                new AiEvent.CultivationCompletedEvent(
                        UUID.randomUUID(), 20L, 3L, "테스트 재배지", BigDecimal.TEN, "/cultivations/3", now);
        CultivationEvent.HarvestCompletedEvent harvest =
                new CultivationEvent.HarvestCompletedEvent(
                        UUID.randomUUID(), 20L, 3L, "테스트 재배지", 5L, BigDecimal.ONE, now);
        CultivationEvent.SensorDataUnavailableEvent sensorUnavailable =
                new CultivationEvent.SensorDataUnavailableEvent(
                        UUID.randomUUID(), 3L, "온도 센서", "센서 데이터 없음", now);
        CultivationEvent.CultivationMemberInvitedEvent memberInvited =
                new CultivationEvent.CultivationMemberInvitedEvent(
                        UUID.randomUUID(), 3L, 20L, "초대자", 21L, "초대 대상", "/invitations/1", now);
        UserEvent.UserLoginAttemptedEvent login =
                new UserEvent.UserLoginAttemptedEvent(UUID.randomUUID(), 20L, "테스터", true, "서울", now);
        UserEvent.UserPasswordChangeAttemptedEvent passwordChange =
                new UserEvent.UserPasswordChangeAttemptedEvent(UUID.randomUUID(), 20L, "테스터", true, now);
        UserEvent.UserAccountReactivationAttemptedEvent reactivation =
                new UserEvent.UserAccountReactivationAttemptedEvent(UUID.randomUUID(), 20L, "테스터", true, now);
        UserEvent.InquirySubmittedEvent inquiry =
                new UserEvent.InquirySubmittedEvent(
                        UUID.randomUUID(), 20L, List.of(1L), 4L, "문의", "재배", "/inquiries/4",
                        UserEvent.InquiryType.QUESTION, now);

        RabbitMqNotificationCommand automationCommand = command("AUTOMATION_STATE_CHANGED");
        RabbitMqNotificationCommand dailyFeedbackCommand = command("DAILY_FEEDBACK_GENERATED");
        RabbitMqNotificationCommand cultivationCompletedCommand = command("CULTIVATION_COMPLETED");
        RabbitMqNotificationCommand harvestCommand = command("HARVEST_COMPLETED");
        RabbitMqNotificationCommand sensorUnavailableCommand = command("SENSOR_DATA_UNAVAILABLE");
        RabbitMqNotificationCommand memberInvitedCommand = command("CULTIVATION_MEMBER_INVITED");
        RabbitMqNotificationCommand loginCommand = command("USER_LOGIN_ATTEMPTED");
        RabbitMqNotificationCommand passwordChangeCommand = command("USER_PASSWORD_CHANGE_ATTEMPTED");
        RabbitMqNotificationCommand reactivationCommand = command("USER_ACCOUNT_REACTIVATION_ATTEMPTED");
        RabbitMqNotificationCommand inquiryCommand = command("INQUIRY_SUBMITTED");

        when(ruleEngineProcessor.process(automation)).thenReturn(automationCommand);
        when(aiProcessor.process(dailyFeedback)).thenReturn(dailyFeedbackCommand);
        when(aiProcessor.process(cultivationCompleted)).thenReturn(cultivationCompletedCommand);
        when(cultivationProcessor.process(harvest)).thenReturn(harvestCommand);
        when(cultivationProcessor.process(sensorUnavailable)).thenReturn(sensorUnavailableCommand);
        when(cultivationProcessor.process(memberInvited)).thenReturn(memberInvitedCommand);
        when(userProcessor.process(login)).thenReturn(loginCommand);
        when(userProcessor.process(passwordChange)).thenReturn(passwordChangeCommand);
        when(userProcessor.process(reactivation)).thenReturn(reactivationCommand);
        when(userProcessor.process(inquiry)).thenReturn(inquiryCommand);

        facade.handle(automation);
        facade.handle(dailyFeedback);
        facade.handle(cultivationCompleted);
        facade.handle(harvest);
        facade.handle(sensorUnavailable);
        facade.handle(memberInvited);
        facade.handle(login);
        facade.handle(passwordChange);
        facade.handle(reactivation);
        facade.handle(inquiry);

        verify(persistenceService).persist(automationCommand);
        verify(persistenceService).persist(dailyFeedbackCommand);
        verify(persistenceService).persist(cultivationCompletedCommand);
        verify(persistenceService).persist(harvestCommand);
        verify(persistenceService).persist(sensorUnavailableCommand);
        verify(persistenceService).persist(memberInvitedCommand);
        verify(persistenceService).persist(loginCommand);
        verify(persistenceService).persist(passwordChangeCommand);
        verify(persistenceService).persist(reactivationCommand);
        verify(persistenceService).persist(inquiryCommand);
        verify(deliveryPersistenceService).activateForDispatch(automationCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(dailyFeedbackCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(cultivationCompletedCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(harvestCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(sensorUnavailableCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(memberInvitedCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(loginCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(passwordChangeCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(reactivationCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(inquiryCommand.eventId());
    }

    @Test
    void 수확과_멤버추가_Envelope를_Processor로_변환해_저장한다() {
        RabbitMqNotificationPersistenceService persistenceService = mock(RabbitMqNotificationPersistenceService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryPersistenceService =
                mock(RabbitMqNotificationDeliveryPersistenceService.class);
        DeliveryDispatchService dispatchService = mock(DeliveryDispatchService.class);
        CultivationNotificationProcessor cultivationProcessor = mock(CultivationNotificationProcessor.class);
        RabbitMqNotificationFacade facade = new RabbitMqNotificationFacade(
                persistenceService,
                deliveryPersistenceService,
                dispatchService,
                mock(RuleEngineNotificationProcessor.class),
                mock(AiNotificationProcessor.class),
                cultivationProcessor,
                mock(UserNotificationProcessor.class));

        NotificationEnvelope<HarvestCompletedPayload> harvest = new NotificationEnvelope<>(
                "7a5bc0a0-b4a7-4c50-b2e2-4d238c234487", "HARVEST_COMPLETED", "cultivation-server",
                "CULTIVATION", 3L, "2026-08-27T14:15:30+09:00",
                new HarvestCompletedPayload("광주", BigDecimal.TEN));
        NotificationEnvelope<MemberAddedPayload> member = new NotificationEnvelope<>(
                "8a5bc0a0-b4a7-4c50-b2e2-4d238c234488", "MEMBER_ADDED", "cultivation-server",
                "USER", 21L, "2026-08-27T14:15:30+09:00",
                new MemberAddedPayload(3L, "광주", "MEMBER"));
        RabbitMqNotificationCommand harvestCommand = command("HARVEST_COMPLETED");
        RabbitMqNotificationCommand memberCommand = command("MEMBER_ADDED");
        when(cultivationProcessor.processHarvestCompleted(harvest)).thenReturn(harvestCommand);
        when(cultivationProcessor.processMemberAdded(member)).thenReturn(memberCommand);

        facade.handleHarvestCompleted(harvest);
        facade.handleMemberAdded(member);

        verify(persistenceService).persist(harvestCommand);
        verify(persistenceService).persist(memberCommand);
        verify(deliveryPersistenceService).activateForDispatch(harvestCommand.eventId());
        verify(deliveryPersistenceService).activateForDispatch(memberCommand.eventId());
    }

    @Test
    void 저장이_끝난_delivery를_PENDING으로_활성화한_후_발송한다() {
        RabbitMqNotificationPersistenceService persistenceService = mock(RabbitMqNotificationPersistenceService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryPersistenceService =
                mock(RabbitMqNotificationDeliveryPersistenceService.class);
        DeliveryDispatchService dispatchService = mock(DeliveryDispatchService.class);
        RuleEngineNotificationProcessor ruleEngineProcessor = mock(RuleEngineNotificationProcessor.class);
        RabbitMqNotificationFacade facade = new RabbitMqNotificationFacade(
                persistenceService,
                deliveryPersistenceService,
                dispatchService,
                ruleEngineProcessor,
                mock(AiNotificationProcessor.class),
                mock(CultivationNotificationProcessor.class),
                mock(UserNotificationProcessor.class));
        UUID eventId = UUID.randomUUID();
        RuleEngineEvent.ThresholdStatusChangedEvent event =
                new RuleEngineEvent.ThresholdStatusChangedEvent(eventId, null, null, OffsetDateTime.now());
        RabbitMqNotificationCommand command = new RabbitMqNotificationCommand(
                eventId,
                "ENVIRONMENT_THRESHOLD_BREACHED",
                "CULTIVATION",
                3L,
                event.occurredAt(),
                Map.of("sensorType", "TEMPERATURE"));

        when(ruleEngineProcessor.process(event)).thenReturn(command);
        when(deliveryPersistenceService.activateForDispatch(eventId)).thenReturn(List.of(11L, 12L));

        facade.handle(event);

        InOrder inOrder = inOrder(ruleEngineProcessor, persistenceService, deliveryPersistenceService, dispatchService);
        inOrder.verify(ruleEngineProcessor).process(event);
        inOrder.verify(persistenceService).persist(command);
        inOrder.verify(deliveryPersistenceService).activateForDispatch(eventId);
        inOrder.verify(dispatchService).dispatch(11L);
        inOrder.verify(dispatchService).dispatch(12L);
        verifyNoMoreInteractions(ruleEngineProcessor, persistenceService, deliveryPersistenceService, dispatchService);
    }

    private RabbitMqNotificationCommand command(String eventType) {
        return new RabbitMqNotificationCommand(
                UUID.randomUUID(),
                eventType,
                "CULTIVATION",
                3L,
                OffsetDateTime.now(),
                Map.of("eventType", eventType));
    }
}
