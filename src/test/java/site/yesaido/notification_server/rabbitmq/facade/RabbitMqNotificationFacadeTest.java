package site.yesaido.notification_server.rabbitmq.facade;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationDeliveryPersistenceService;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationPersistenceService;
import site.yesaido.notification_server.rabbitmq.processor.AiNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.CultivationNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.RuleEngineNotificationProcessor;
import site.yesaido.notification_server.rabbitmq.processor.UserNotificationProcessor;
import site.yesaido.notification_server.service.DeliveryDispatchService;

class RabbitMqNotificationFacadeTest {

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
}
