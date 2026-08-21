package site.yesaido.notification_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointUpdateRequest;
import site.yesaido.notification_server.entity.NotificationSubscription;
import site.yesaido.notification_server.exception.endpoint.DuplicateNotificationEndpointException;
import site.yesaido.notification_server.exception.endpoint.NotificationChannelNotFoundException;
import site.yesaido.notification_server.exception.endpoint.NotificationEndpointNotFoundException;
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
        EndpointCreateRequest request = new EndpointCreateRequest(1L, "123456", "내 텔레그램");

        assertThatThrownBy(() -> service.create(
                7L, request))
                .isInstanceOf(DuplicateNotificationEndpointException.class);

        verify(endpointRepository, never()).save(org.mockito.ArgumentMatchers.any(
                NotificationEndpoint.class));
    }

    @Test
    void 존재하지_않는_채널로_Endpoint를_만들수_없다() {
        when(channelTypeRepository.findByIdAndDeletedFalse(99L)).thenReturn(Optional.empty());
        EndpointCreateRequest request = new EndpointCreateRequest(99L, "destination", "없는 채널");

        assertThatThrownBy(() -> service.create(
                7L, request))
                .isInstanceOf(NotificationChannelNotFoundException.class);

        verify(senderRegistry, never()).get(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void 사용자의_활성_Endpoint_목록을_응답으로_변환한다() {
        NotificationEndpoint endpoint = endpoint(10L, 7L, 1L, "TELEGRAM", "123456", "내 텔레그램");
        when(endpointRepository.findAllActiveByUserId(7L)).thenReturn(List.of(endpoint));

        var responses = service.findAll(7L);

        assertThat(responses).hasSize(1);
        assertThat(responses.getFirst().id()).isEqualTo(10L);
        assertThat(responses.getFirst().destination()).endsWith("3456");
    }

    @Test
    void 소유한_Endpoint를_검증후_수정한다() {
        NotificationEndpoint endpoint = endpoint(10L, 7L, 2L, "DISCORD",
                "https://discord.com/api/webhooks/old/token", "기존");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(10L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(senderRegistry.get("DISCORD")).thenReturn(sender);
        EndpointUpdateRequest request = new EndpointUpdateRequest(
                "https://discord.com/api/webhooks/new/token", "새 이름");

        service.update(7L, 10L, request);

        verify(sender).validateDestination(request.destination());
        verify(endpoint).update(request.destination(), request.displayName());
    }

    @Test
    void 수정하려는_목적지가_다른_Endpoint와_중복되면_거절한다() {
        NotificationEndpoint endpoint = endpoint(10L, 7L, 2L, "DISCORD",
                "https://discord.com/api/webhooks/old/token", "기존");
        EndpointUpdateRequest request = new EndpointUpdateRequest(
                "https://discord.com/api/webhooks/duplicate/token", "중복");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(10L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(senderRegistry.get("DISCORD")).thenReturn(sender);
        when(endpointRepository.existsByUserIdAndChannelType_IdAndDestinationAndIdNotAndDeletedFalse(
                7L, 2L, request.destination(), 10L)).thenReturn(true);

        assertThatThrownBy(() -> service.update(7L, 10L, request))
                .isInstanceOf(DuplicateNotificationEndpointException.class);

        verify(endpoint, never()).update(request.destination(), request.displayName());
    }

    @Test
    void Endpoint_활성상태를_변경한다() {
        NotificationEndpoint endpoint = endpoint(10L, 7L, 2L, "DISCORD",
                "https://discord.com/api/webhooks/1/token", "Discord");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(10L, 7L))
                .thenReturn(Optional.of(endpoint));

        service.changeEnabled(7L, 10L, false);

        verify(endpoint).changeEnabled(false);
    }

    @Test
    void 다른_사용자의_Endpoint는_조회하거나_변경할수_없다() {
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(10L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeEnabled(7L, 10L, false))
                .isInstanceOf(NotificationEndpointNotFoundException.class);
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

    private NotificationEndpoint endpoint(Long id, Long userId, Long channelId, String channelCode,
                                          String destination, String displayName) {
        NotificationEndpoint endpoint = mock(
                NotificationEndpoint.class, org.mockito.Mockito.RETURNS_DEEP_STUBS);
        when(endpoint.getId()).thenReturn(id);
        when(endpoint.getUserId()).thenReturn(userId);
        when(endpoint.getChannelType().getId()).thenReturn(channelId);
        when(endpoint.getChannelType().getCode()).thenReturn(channelCode);
        when(endpoint.getChannelType().getDisplayName()).thenReturn(channelCode);
        when(endpoint.getDestination()).thenReturn(destination);
        when(endpoint.getDisplayName()).thenReturn(displayName);
        when(endpoint.isEnabled()).thenReturn(true);
        return endpoint;
    }
}
