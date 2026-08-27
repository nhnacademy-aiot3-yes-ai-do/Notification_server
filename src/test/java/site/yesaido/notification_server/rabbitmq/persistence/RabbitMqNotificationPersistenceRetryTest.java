package site.yesaido.notification_server.rabbitmq.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyShort;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.inOrder;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;
import site.yesaido.notification_server.config.property.NotificationProperties;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.DeliveryStatus;
import site.yesaido.notification_server.entity.NotificationDelivery;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.entity.NotificationSubscription;
import site.yesaido.notification_server.entity.NotificationTemplate;
import site.yesaido.notification_server.entity.SubscriptionTargetType;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationCreationService.RabbitMqNotificationCreationResult;
import site.yesaido.notification_server.rabbitmq.template.RabbitMqTemplateRenderer;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;

class RabbitMqNotificationPersistenceRetryTest {

    private static final OffsetDateTime OCCURRED_AT =
            OffsetDateTime.parse("2026-08-23T12:34:56+09:00");

    @Test
    void deliveryPersistenceService_persist는_repository의_upsertCreatedFromRabbitMqFanout만_호출한다() {
        NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
        RabbitMqNotificationDeliveryPersistenceService service =
                new RabbitMqNotificationDeliveryPersistenceService(deliveryRepository);

        service.persist(99L, 1L, 101L, "rendered");

        verify(deliveryRepository).upsertCreatedFromRabbitMqFanout(99L, 1L, 101L, "rendered");
        verifyNoMoreInteractions(deliveryRepository);
    }

    @Test
    void deliveryPersistenceService_persistFailure는_repository의_insertFailedFromRabbitMqFanout만_호출한다() {
        NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
        RabbitMqNotificationDeliveryPersistenceService service =
                new RabbitMqNotificationDeliveryPersistenceService(deliveryRepository);

        service.persistFailure(99L, 1L, null, (short) 3, "실패 사유");

        verify(deliveryRepository).insertFailedFromRabbitMqFanout(99L, 1L, null, (short) 3, "실패 사유");
        verifyNoMoreInteractions(deliveryRepository);
    }

    @Test
    void deliveryPersistenceService_activateForDispatch는_CREATED를_PENDING으로_바꾼_후_ID를_조회한다() {
        NotificationDeliveryRepository deliveryRepository = mock(NotificationDeliveryRepository.class);
        RabbitMqNotificationDeliveryPersistenceService service =
                new RabbitMqNotificationDeliveryPersistenceService(deliveryRepository);
        UUID eventId = UUID.randomUUID();
        NotificationDelivery first = mock(NotificationDelivery.class);
        NotificationDelivery second = mock(NotificationDelivery.class);
        when(first.getId()).thenReturn(11L);
        when(second.getId()).thenReturn(12L);
        when(deliveryRepository.findAllByNotification_SourceEventIdAndStatusOrderByIdAsc(
                eventId, DeliveryStatus.CREATED)).thenReturn(List.of(first, second));

        assertThat(service.activateForDispatch(eventId)).containsExactly(11L, 12L);

        InOrder inOrder = inOrder(deliveryRepository, first, second);
        inOrder.verify(deliveryRepository).findAllByNotification_SourceEventIdAndStatusOrderByIdAsc(
                eventId, DeliveryStatus.CREATED);
        inOrder.verify(first).activateForDispatch();
        inOrder.verify(second).activateForDispatch();
        inOrder.verify(first).getId();
        inOrder.verify(second).getId();
        verifyNoMoreInteractions(deliveryRepository, first, second);
    }

    @Test
    void firstPassFailureIsRetriedAfterOtherSubscriptionsAndThenPersisted() {
        NotificationEventTypeRepository eventTypeRepository = mock(NotificationEventTypeRepository.class);
        NotificationSubscriptionRepository subscriptionRepository = mock(NotificationSubscriptionRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        RabbitMqNotificationCreationService creationService = mock(RabbitMqNotificationCreationService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryService = mock(RabbitMqNotificationDeliveryPersistenceService.class);
        RabbitMqTemplateRenderer renderer = mock(RabbitMqTemplateRenderer.class);
        RabbitMqNotificationPersistenceService service = new RabbitMqNotificationPersistenceService(
                eventTypeRepository, subscriptionRepository, templateRepository, creationService, deliveryService,
                renderer, new NotificationProperties(null, new NotificationProperties.Retry(Duration.ofMillis(1))));

        NotificationEventType eventType = eventType("CULTIVATION");
        NotificationSubscription first = subscription(1L, 10L);
        NotificationSubscription second = subscription(2L, 20L);
        NotificationTemplate firstTemplate = template(101L, 10L, "first");
        NotificationTemplate secondTemplate = template(102L, 20L, "second");
        RabbitMqNotificationCommand command = new RabbitMqNotificationCommand(
                UUID.randomUUID(), "HARVEST_COMPLETED", "CULTIVATION", 7L, OCCURRED_AT,
                Map.of("cultivationName", "tomato"));

        when(eventTypeRepository.findByCode(command.eventCode())).thenReturn(Optional.of(eventType));
        when(creationService.createIfAbsent(
                eq(command.eventId()), eq(eventType), eq(command.targetId()), eq(OCCURRED_AT), any()))
                .thenReturn(new RabbitMqNotificationCreationResult(99L, true));
        when(subscriptionRepository.findActiveSubscriptions(command.eventCode(), command.targetType(), command.targetId()))
                .thenReturn(List.of(first, second));
        when(templateRepository.findLatestByEventTypeIdAndChannelTypeIds(eq(1L), any()))
                .thenReturn(List.of(firstTemplate, secondTemplate));
        when(renderer.render("first", command.payload()))
                .thenThrow(new IllegalStateException("temporary render failure"))
                .thenReturn("first-rendered");
        when(renderer.render("second", command.payload())).thenReturn("second-rendered");

        assertThat(service.persist(command)).isEqualTo(RabbitMqPersistenceResult.PERSISTED);

        verify(deliveryService).persist(99L, 2L, 102L, "second-rendered");
        verify(deliveryService).persist(99L, 1L, 101L, "first-rendered");
    }

    @Test
    void dbPersistenceFailureDuringDeliveryPersistPropagatesWithoutBeingCaught() {
        NotificationEventTypeRepository eventTypeRepository = mock(NotificationEventTypeRepository.class);
        NotificationSubscriptionRepository subscriptionRepository = mock(NotificationSubscriptionRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        RabbitMqNotificationCreationService creationService = mock(RabbitMqNotificationCreationService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryService = mock(RabbitMqNotificationDeliveryPersistenceService.class);
        RabbitMqTemplateRenderer renderer = mock(RabbitMqTemplateRenderer.class);
        RabbitMqNotificationPersistenceService service = new RabbitMqNotificationPersistenceService(
                eventTypeRepository, subscriptionRepository, templateRepository, creationService, deliveryService,
                renderer, new NotificationProperties(null, new NotificationProperties.Retry(Duration.ofMillis(1))));

        NotificationEventType eventType = eventType("CULTIVATION");
        NotificationSubscription subscription = subscription(1L, 10L);
        NotificationTemplate template = template(101L, 10L, "body");
        RabbitMqNotificationCommand command = new RabbitMqNotificationCommand(
                UUID.randomUUID(), "HARVEST_COMPLETED", "CULTIVATION", 7L, OCCURRED_AT, Map.of());
        when(eventTypeRepository.findByCode(command.eventCode())).thenReturn(Optional.of(eventType));
        when(creationService.createIfAbsent(
                eq(command.eventId()), eq(eventType), eq(command.targetId()), eq(OCCURRED_AT), any()))
                .thenReturn(new RabbitMqNotificationCreationResult(99L, true));
        when(subscriptionRepository.findActiveSubscriptions(command.eventCode(), command.targetType(), command.targetId()))
                .thenReturn(List.of(subscription));
        when(templateRepository.findLatestByEventTypeIdAndChannelTypeIds(eq(1L), any())).thenReturn(List.of(template));
        when(renderer.render("body", command.payload())).thenReturn("rendered");
        RuntimeException dbFailure = new RuntimeException("db unavailable");
        doThrow(dbFailure).when(deliveryService).persist(99L, 1L, 101L, "rendered");

        assertThatThrownBy(() -> service.persist(command)).isSameAs(dbFailure);

        verify(deliveryService, never()).persistFailure(any(), any(), any(), anyShort(), any());
    }

    @Test
    void exhaustedFailureIsPersistedAsFailedDeliveryAfterThreeAttempts() {
        NotificationEventTypeRepository eventTypeRepository = mock(NotificationEventTypeRepository.class);
        NotificationSubscriptionRepository subscriptionRepository = mock(NotificationSubscriptionRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        RabbitMqNotificationCreationService creationService = mock(RabbitMqNotificationCreationService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryService = mock(RabbitMqNotificationDeliveryPersistenceService.class);
        RabbitMqTemplateRenderer renderer = mock(RabbitMqTemplateRenderer.class);
        RabbitMqNotificationPersistenceService service = new RabbitMqNotificationPersistenceService(
                eventTypeRepository, subscriptionRepository, templateRepository, creationService, deliveryService,
                renderer, new NotificationProperties(null, new NotificationProperties.Retry(Duration.ofMillis(1))));

        NotificationEventType eventType = eventType("CULTIVATION");
        NotificationSubscription subscription = subscription(1L, 10L);
        NotificationTemplate template = template(101L, 10L, "body");
        RabbitMqNotificationCommand command = new RabbitMqNotificationCommand(
                UUID.randomUUID(), "HARVEST_COMPLETED", "CULTIVATION", 7L, OCCURRED_AT, Map.of());
        when(eventTypeRepository.findByCode(command.eventCode())).thenReturn(Optional.of(eventType));
        when(creationService.createIfAbsent(
                eq(command.eventId()), eq(eventType), eq(command.targetId()), eq(OCCURRED_AT), any()))
                .thenReturn(new RabbitMqNotificationCreationResult(99L, true));
        when(subscriptionRepository.findActiveSubscriptions(command.eventCode(), command.targetType(), command.targetId()))
                .thenReturn(List.of(subscription));
        when(templateRepository.findLatestByEventTypeIdAndChannelTypeIds(eq(1L), any())).thenReturn(List.of(template));
        when(renderer.render("body", command.payload())).thenThrow(new IllegalStateException("render failure"));

        assertThat(service.persist(command)).isEqualTo(RabbitMqPersistenceResult.PERSISTED);

        verify(renderer, times(3)).render("body", command.payload());
        verify(deliveryService).persistFailure(99L, 1L, 101L, (short) 3, "render failure");
    }

    @Test
    void persistFailureItselfFailingPropagatesInsteadOfBeingSwallowed() {
        NotificationEventTypeRepository eventTypeRepository = mock(NotificationEventTypeRepository.class);
        NotificationSubscriptionRepository subscriptionRepository = mock(NotificationSubscriptionRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        RabbitMqNotificationCreationService creationService = mock(RabbitMqNotificationCreationService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryService = mock(RabbitMqNotificationDeliveryPersistenceService.class);
        RabbitMqTemplateRenderer renderer = mock(RabbitMqTemplateRenderer.class);
        RabbitMqNotificationPersistenceService service = new RabbitMqNotificationPersistenceService(
                eventTypeRepository, subscriptionRepository, templateRepository, creationService, deliveryService,
                renderer, new NotificationProperties(null, new NotificationProperties.Retry(Duration.ofMillis(1))));

        NotificationEventType eventType = eventType("CULTIVATION");
        NotificationSubscription subscription = subscription(1L, 10L);
        NotificationTemplate template = template(101L, 10L, "body");
        RabbitMqNotificationCommand command = new RabbitMqNotificationCommand(
                UUID.randomUUID(), "HARVEST_COMPLETED", "CULTIVATION", 7L, OCCURRED_AT, Map.of());
        when(eventTypeRepository.findByCode(command.eventCode())).thenReturn(Optional.of(eventType));
        when(creationService.createIfAbsent(
                eq(command.eventId()), eq(eventType), eq(command.targetId()), eq(OCCURRED_AT), any()))
                .thenReturn(new RabbitMqNotificationCreationResult(99L, true));
        when(subscriptionRepository.findActiveSubscriptions(command.eventCode(), command.targetType(), command.targetId()))
                .thenReturn(List.of(subscription));
        when(templateRepository.findLatestByEventTypeIdAndChannelTypeIds(eq(1L), any())).thenReturn(List.of(template));
        when(renderer.render("body", command.payload())).thenThrow(new IllegalStateException("render failure"));
        RuntimeException persistenceFailure = new RuntimeException("db unavailable");
        doThrow(persistenceFailure)
                .when(deliveryService).persistFailure(99L, 1L, 101L, (short) 3, "render failure");

        assertThatThrownBy(() -> service.persist(command))
                .isInstanceOf(RuntimeException.class)
                .extracting(Throwable::getCause)
                .isSameAs(persistenceFailure);
    }

    @Test
    void missingTemplateIsPersistedAsFailedDeliveryWithoutTemplateReference() {
        NotificationEventTypeRepository eventTypeRepository = mock(NotificationEventTypeRepository.class);
        NotificationSubscriptionRepository subscriptionRepository = mock(NotificationSubscriptionRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        RabbitMqNotificationCreationService creationService = mock(RabbitMqNotificationCreationService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryService = mock(RabbitMqNotificationDeliveryPersistenceService.class);
        RabbitMqTemplateRenderer renderer = mock(RabbitMqTemplateRenderer.class);
        RabbitMqNotificationPersistenceService service = new RabbitMqNotificationPersistenceService(
                eventTypeRepository, subscriptionRepository, templateRepository, creationService, deliveryService,
                renderer, new NotificationProperties(null, new NotificationProperties.Retry(Duration.ofMillis(1))));

        NotificationEventType eventType = eventType("CULTIVATION");
        NotificationSubscription subscription = subscription(1L, 10L);
        RabbitMqNotificationCommand command = new RabbitMqNotificationCommand(
                UUID.randomUUID(), "HARVEST_COMPLETED", "CULTIVATION", 7L, OCCURRED_AT, Map.of());
        when(eventTypeRepository.findByCode(command.eventCode())).thenReturn(Optional.of(eventType));
        when(creationService.createIfAbsent(
                eq(command.eventId()), eq(eventType), eq(command.targetId()), eq(OCCURRED_AT), any()))
                .thenReturn(new RabbitMqNotificationCreationResult(99L, true));
        when(subscriptionRepository.findActiveSubscriptions(command.eventCode(), command.targetType(), command.targetId()))
                .thenReturn(List.of(subscription));
        when(templateRepository.findLatestByEventTypeIdAndChannelTypeIds(eq(1L), any())).thenReturn(List.of());

        assertThat(service.persist(command)).isEqualTo(RabbitMqPersistenceResult.PERSISTED);

        verify(deliveryService).persistFailure(99L, 1L, null, (short) 3, "template not found for channelTypeId=10");
    }

    private NotificationEventType eventType(String targetTypeCode) {
        NotificationEventType eventType = mock(NotificationEventType.class);
        SubscriptionTargetType targetType = mock(SubscriptionTargetType.class);
        when(eventType.getId()).thenReturn(1L);
        when(eventType.getTargetType()).thenReturn(targetType);
        when(targetType.getTargetType()).thenReturn(targetTypeCode);
        return eventType;
    }

    private NotificationSubscription subscription(Long id, Long channelTypeId) {
        NotificationSubscription subscription = mock(NotificationSubscription.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(subscription.getId()).thenReturn(id);
        when(subscription.getEndpoint().getChannelType().getId()).thenReturn(channelTypeId);
        return subscription;
    }

    private NotificationTemplate template(Long id, Long channelTypeId, String body) {
        NotificationTemplate template = mock(NotificationTemplate.class);
        ChannelType channelType = mock(ChannelType.class);
        when(template.getId()).thenReturn(id);
        when(template.getChannelType()).thenReturn(channelType);
        when(channelType.getId()).thenReturn(channelTypeId);
        when(template.getBodyTemplate()).thenReturn(body);
        return template;
    }
}
