package site.yesaido.notification_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.entity.NotificationSubscription;
import site.yesaido.notification_server.exception.endpoint.DuplicateNotificationEndpointException;
import site.yesaido.notification_server.provider.NotificationSender;
import site.yesaido.notification_server.provider.NotificationSenderRegistry;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
class NotificationEndpointServiceTest {

    private final NotificationEndpointRepository endpointRepository =
            mock(NotificationEndpointRepository.class);
    private final ChannelTypeRepository channelTypeRepository =
            mock(ChannelTypeRepository.class);
    private final NotificationSubscriptionRepository subscriptionRepository =
            mock(NotificationSubscriptionRepository.class);
    private final NotificationSenderRegistry senderRegistry =
            mock(NotificationSenderRegistry.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private NotificationEndpointService service;

    @BeforeEach
    void setUp() {
        service = new NotificationEndpointService(
                endpointRepository, channelTypeRepository, subscriptionRepository, senderRegistry);
    }

    @Test
    void createsEndpointAfterChannelValidation() {
        ChannelType channel = mock(ChannelType.class);
        when(channel.getId()).thenReturn(1L);
        when(channel.getCode()).thenReturn("TELEGRAM");
        when(channel.getDisplayName()).thenReturn("Telegram");
        when(channelTypeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(channel));
        when(senderRegistry.get("TELEGRAM")).thenReturn(sender);
        when(endpointRepository.save(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(
                7L, new EndpointCreateRequest(1L, "123456", "내 텔레그램"));

        assertThat(response.channelCode()).isEqualTo("TELEGRAM");
        assertThat(response.destination()).endsWith("3456").doesNotContain("123456");
        assertThat(response.enabled()).isTrue();
        verify(sender).validateDestination("123456");
    }

    @Test
    void rejectsDuplicateEndpoint() {
        ChannelType channel = mock(ChannelType.class);
        when(channel.getId()).thenReturn(1L);
        when(channel.getCode()).thenReturn("TELEGRAM");
        when(channelTypeRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(channel));
        when(senderRegistry.get("TELEGRAM")).thenReturn(sender);
        when(endpointRepository
                .existsByUserIdAndChannelType_IdAndDestinationAndDeletedFalse(
                        7L, 1L, "123456"))
                .thenReturn(true);

        assertThatThrownBy(() -> service.create(
                7L, new EndpointCreateRequest(1L, "123456", "내 텔레그램")))
                .isInstanceOf(DuplicateNotificationEndpointException.class);

        verify(endpointRepository, never()).save(org.mockito.ArgumentMatchers.any(
                NotificationEndpoint.class));
    }

    @Test
    void deletingEndpointAlsoSoftDeletesActiveSubscriptions() {
        NotificationEndpoint endpoint = mock(NotificationEndpoint.class);
        var firstSubscription = mock(NotificationSubscription.class);
        var secondSubscription = mock(NotificationSubscription.class);
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(10L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(subscriptionRepository.findAllByEndpoint_IdAndDeletedFalse(10L))
                .thenReturn(java.util.List.of(firstSubscription, secondSubscription));

        service.delete(7L, 10L);

        verify(endpoint).softDelete();
        verify(firstSubscription).softDelete();
        verify(secondSubscription).softDelete();
    }
}
