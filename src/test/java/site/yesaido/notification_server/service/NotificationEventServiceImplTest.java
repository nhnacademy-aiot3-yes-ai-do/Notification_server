package site.yesaido.notification_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.domain.ChannelType;
import site.yesaido.notification_server.domain.NotificationDelivery;
import site.yesaido.notification_server.domain.NotificationEndpoint;
import site.yesaido.notification_server.domain.NotificationEventType;
import site.yesaido.notification_server.domain.NotificationSubscription;
import site.yesaido.notification_server.domain.NotificationTemplate;
import site.yesaido.notification_server.domain.SubscriptionTargetType;
import site.yesaido.notification_server.messaging.DomainEvent;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;
import site.yesaido.notification_server.service.impl.NotificationEventServiceImpl;
import site.yesaido.notification_server.template.TemplateRenderer;

class NotificationEventServiceImplTest {

    private final NotificationRepository notificationRepository =
            mock(NotificationRepository.class);
    private final NotificationEventTypeRepository eventTypeRepository =
            mock(NotificationEventTypeRepository.class);
    private final NotificationSubscriptionRepository subscriptionRepository =
            mock(NotificationSubscriptionRepository.class);
    private final NotificationTemplateRepository templateRepository =
            mock(NotificationTemplateRepository.class);
    private final NotificationDeliveryRepository deliveryRepository =
            mock(NotificationDeliveryRepository.class);
    private final TemplateRenderer renderer = mock(TemplateRenderer.class);
    private NotificationEventServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationEventServiceImpl(
                notificationRepository,
                eventTypeRepository,
                subscriptionRepository,
                templateRepository,
                deliveryRepository,
                renderer,
                new ObjectMapper());
    }

    @Test
    void ignoresDuplicateSourceEvent() {
        DomainEvent event = event();
        when(notificationRepository.existsBySourceEventId(event.eventId())).thenReturn(true);

        EventProcessingResult result = service.process(event);

        assertThat(result.duplicate()).isTrue();
        verify(eventTypeRepository, never()).findByCode(any());
    }

    @Test
    void reportsWhetherEventWasAlreadyPersisted() {
        UUID eventId = UUID.randomUUID();
        when(notificationRepository.existsBySourceEventId(eventId)).thenReturn(true);

        boolean processed = service.isProcessed(eventId);

        assertThat(processed).isTrue();
    }

    @Test
    void createsDeliveryForEachActiveSubscription() {
        DomainEvent event = event();
        SubscriptionTargetType targetType = mock(SubscriptionTargetType.class);
        NotificationEventType eventType = mock(NotificationEventType.class);
        NotificationSubscription subscription = mock(NotificationSubscription.class);
        NotificationEndpoint endpoint = mock(NotificationEndpoint.class);
        ChannelType channel = mock(ChannelType.class);
        NotificationTemplate template = mock(NotificationTemplate.class);
        NotificationDelivery savedDelivery = mock(NotificationDelivery.class);

        when(eventType.getId()).thenReturn(1L);
        when(eventType.getDisplayName()).thenReturn("센서 오류");
        when(eventType.getTargetType()).thenReturn(targetType);
        when(targetType.getTargetType()).thenReturn("CULTIVATION");
        when(subscription.getEndpoint()).thenReturn(endpoint);
        when(endpoint.getChannelType()).thenReturn(channel);
        when(channel.getId()).thenReturn(2L);
        when(template.getBodyTemplate()).thenReturn("{{errorMessage}}");
        when(savedDelivery.getId()).thenReturn(77L);

        when(eventTypeRepository.findByCode("SENSOR_ERROR")).thenReturn(Optional.of(eventType));
        when(subscriptionRepository.findActiveSubscriptions(
                "SENSOR_ERROR", "CULTIVATION", 101L))
                .thenReturn(List.of(subscription));
        when(templateRepository
                .findFirstByEventType_IdAndChannelType_IdOrderByVersionDesc(1L, 2L))
                .thenReturn(Optional.of(template));
        when(renderer.render("{{errorMessage}}", event)).thenReturn("통신 오류");
        when(deliveryRepository.save(any(NotificationDelivery.class))).thenReturn(savedDelivery);

        EventProcessingResult result = service.process(event);

        assertThat(result.deliveryIds()).containsExactly(77L);
        verify(notificationRepository).save(any());
        verify(deliveryRepository).save(any(NotificationDelivery.class));
    }

    private DomainEvent event() {
        ObjectMapper objectMapper = new ObjectMapper();
        return new DomainEvent(
                UUID.randomUUID(),
                "SENSOR_ERROR",
                "rule-server",
                "CULTIVATION",
                101L,
                OffsetDateTime.parse("2026-07-31T10:00:00+09:00"),
                objectMapper.createObjectNode().put("errorMessage", "통신 오류"));
    }
}
