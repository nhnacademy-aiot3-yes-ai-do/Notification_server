package site.yesaido.notification_server.rabbitmq.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.entity.NotificationSubscription;
import site.yesaido.notification_server.entity.NotificationTemplate;
import site.yesaido.notification_server.entity.SubscriptionTargetType;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationCreationService.RabbitMqNotificationCreationResult;
import site.yesaido.notification_server.rabbitmq.template.RabbitMqTemplateRenderer;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;

class RabbitMqNotificationPersistenceServiceTest {

    @Test
    void template이_없는_구독은_건너뛰고_다른_구독의_delivery는_저장한다() {
        NotificationEventTypeRepository eventTypeRepository = mock(NotificationEventTypeRepository.class);
        NotificationSubscriptionRepository subscriptionRepository = mock(NotificationSubscriptionRepository.class);
        NotificationTemplateRepository templateRepository = mock(NotificationTemplateRepository.class);
        RabbitMqNotificationCreationService creationService = mock(RabbitMqNotificationCreationService.class);
        RabbitMqNotificationDeliveryPersistenceService deliveryService = mock(RabbitMqNotificationDeliveryPersistenceService.class);
        RabbitMqTemplateRenderer templateRenderer = mock(RabbitMqTemplateRenderer.class);
        RabbitMqNotificationPersistenceService service = new RabbitMqNotificationPersistenceService(
                eventTypeRepository, subscriptionRepository, templateRepository, creationService, deliveryService, templateRenderer,
                new NotificationProperties(null, new NotificationProperties.Retry(Duration.ofMillis(1))));

        NotificationEventType eventType = eventType("CULTIVATION");
        NotificationSubscription telegramSubscription = subscription(1L, 10L);
        NotificationSubscription discordSubscription = subscription(2L, 20L);
        NotificationTemplate telegramTemplate = template(101L, 10L, "template");
        RabbitMqNotificationCommand command = new RabbitMqNotificationCommand(
                UUID.randomUUID(), "HARVEST_COMPLETED", "CULTIVATION", 7L, null, Map.of("cultivationName", "토마토"));

        when(eventTypeRepository.findByCode(command.eventCode())).thenReturn(Optional.of(eventType));
        when(creationService.createIfAbsent(eq(command.eventId()), eq(eventType), any()))
                .thenReturn(new RabbitMqNotificationCreationResult(99L, true));
        when(subscriptionRepository.findActiveSubscriptions(command.eventCode(), command.targetType(), command.targetId()))
                .thenReturn(List.of(telegramSubscription, discordSubscription));
        when(templateRepository.findLatestByEventTypeIdAndChannelTypeIds(eq(eventType.getId()), any()))
                .thenReturn(List.of(telegramTemplate));
        when(templateRenderer.render("template", command.payload())).thenReturn("rendered");

        assertThat(service.persist(command)).isEqualTo(RabbitMqPersistenceResult.PERSISTED);

        verify(deliveryService).persist(99L, 1L, 101L, "rendered");
        verify(deliveryService, org.mockito.Mockito.never()).persist(
                eq(99L), eq(2L), any(), any());
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
