package site.yesaido.notification_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.domain.ChannelType;
import site.yesaido.notification_server.domain.NotificationEndpoint;
import site.yesaido.notification_server.domain.NotificationEventType;
import site.yesaido.notification_server.domain.NotificationSubscription;
import site.yesaido.notification_server.domain.NotificationSubscriptionType;
import site.yesaido.notification_server.domain.SubscriptionTargetType;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.exception.NotificationNotFoundException;
import site.yesaido.notification_server.exception.UnsupportedNotificationChannelException;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionTypeRepository;
import site.yesaido.notification_server.repository.SubscriptionChannelRepository;
class NotificationSubscriptionServiceTest {

    private final NotificationSubscriptionRepository subscriptionRepository =
            mock(NotificationSubscriptionRepository.class);
    private final NotificationSubscriptionTypeRepository typeRepository =
            mock(NotificationSubscriptionTypeRepository.class);
    private final NotificationEndpointRepository endpointRepository =
            mock(NotificationEndpointRepository.class);
    private final SubscriptionChannelRepository channelRepository =
            mock(SubscriptionChannelRepository.class);
    private NotificationSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSubscriptionService(
                subscriptionRepository, typeRepository, endpointRepository, channelRepository);
    }

    @Test
    void reactivatesExistingPausedSubscription() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L);
        NotificationSubscription subscription = mock(NotificationSubscription.class);
        when(subscription.getSubscriptionType()).thenReturn(type);
        when(subscription.getEndpoint()).thenReturn(endpoint);
        when(subscription.getTargetId()).thenReturn(101L);
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));
        when(channelRepository.existsBySubscriptionType_IdAndChannelType_Id(22L, 1L))
                .thenReturn(true);
        when(subscriptionRepository
                .findBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndDeletedFalse(
                        22L, 11L, 101L))
                .thenReturn(Optional.of(subscription));

        var response = service.create(
                7L, new SubscriptionCreateRequest(22L, 11L, 101L));

        assertThat(response.targetId()).isEqualTo(101L);
        verify(subscription).changeEnabled(true);
    }

    @Test
    void rejectsChannelNotSupportedBySubscriptionType() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L);
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.create(
                7L, new SubscriptionCreateRequest(22L, 11L, 101L)))
                .isInstanceOf(UnsupportedNotificationChannelException.class);
    }

    @Test
    void rejectsAnotherUserAsUserTarget() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L, "USER");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));

        assertThatThrownBy(() -> service.create(
                7L, new SubscriptionCreateRequest(22L, 11L, 8L)))
                .isInstanceOf(NotificationNotFoundException.class);
    }

    private NotificationEndpoint endpoint(Long id, Long channelId, String channelCode) {
        NotificationEndpoint endpoint = mock(NotificationEndpoint.class);
        ChannelType channel = mock(ChannelType.class);
        when(endpoint.getId()).thenReturn(id);
        when(endpoint.getChannelType()).thenReturn(channel);
        when(channel.getId()).thenReturn(channelId);
        when(channel.getCode()).thenReturn(channelCode);
        return endpoint;
    }

    private NotificationSubscriptionType subscriptionType(Long id) {
        return subscriptionType(id, "CULTIVATION");
    }

    private NotificationSubscriptionType subscriptionType(Long id, String targetCode) {
        NotificationSubscriptionType type = mock(NotificationSubscriptionType.class);
        NotificationEventType eventType = mock(NotificationEventType.class);
        SubscriptionTargetType targetType = mock(SubscriptionTargetType.class);
        when(type.getId()).thenReturn(id);
        when(type.getName()).thenReturn("센서 오류 알림");
        when(type.getEventType()).thenReturn(eventType);
        when(type.getTargetType()).thenReturn(targetType);
        when(eventType.getCode()).thenReturn("SENSOR_ERROR");
        when(targetType.getTargetType()).thenReturn(targetCode);
        return type;
    }
}
