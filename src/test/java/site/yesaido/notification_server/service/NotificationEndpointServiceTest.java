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
import site.yesaido.notification_server.domain.ChannelType;
import site.yesaido.notification_server.domain.NotificationEndpoint;
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.exception.DuplicateNotificationResourceException;
import site.yesaido.notification_server.provider.NotificationSender;
import site.yesaido.notification_server.provider.NotificationSenderRegistry;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;
class NotificationEndpointServiceTest {

    private final NotificationEndpointRepository endpointRepository =
            mock(NotificationEndpointRepository.class);
    private final ChannelTypeRepository channelTypeRepository =
            mock(ChannelTypeRepository.class);
    private final NotificationSenderRegistry senderRegistry =
            mock(NotificationSenderRegistry.class);
    private final NotificationSender sender = mock(NotificationSender.class);
    private NotificationEndpointService service;

    @BeforeEach
    void setUp() {
        service = new NotificationEndpointService(
                endpointRepository, channelTypeRepository, senderRegistry);
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
                .isInstanceOf(DuplicateNotificationResourceException.class);

        verify(endpointRepository, never()).save(org.mockito.ArgumentMatchers.any(
                NotificationEndpoint.class));
    }
}
