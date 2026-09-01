package site.yesaido.notification_server.service;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import site.yesaido.common.exception.client.ConflictException;
import site.yesaido.notification_server.dto.admin.ChannelTypeRequest;
import site.yesaido.notification_server.dto.admin.NotificationTemplateRequest;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;
import site.yesaido.notification_server.repository.SubscriptionChannelRepository;

class NotificationTemplateChannelAdminServiceTest {

    @Mock
    private NotificationTemplateRepository templateRepository;
    @Mock
    private NotificationEventTypeRepository eventRepository;
    @Mock
    private ChannelTypeRepository channelRepository;
    @Mock
    private NotificationDeliveryRepository deliveryRepository;
    @Mock
    private SubscriptionChannelRepository subscriptionChannelRepository;

    private NotificationTemplateChannelAdminService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new NotificationTemplateChannelAdminService(
                templateRepository, eventRepository, channelRepository,
                deliveryRepository, subscriptionChannelRepository);
    }

    @Test
    void rejectsDuplicateTemplateVersionEvenWhenItIsNotTheLatestVersion() {
        NotificationEventType event = org.mockito.Mockito.mock(NotificationEventType.class);
        ChannelType channel = org.mockito.Mockito.mock(ChannelType.class);
        when(event.getId()).thenReturn(1L);
        when(channel.getId()).thenReturn(2L);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(channelRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.of(channel));
        when(templateRepository.existsByEventType_IdAndChannelType_IdAndVersion(1L, 2L, 2))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createTemplate(
                new NotificationTemplateRequest(1L, 2L, "본문", 2)))
                .isInstanceOf(ConflictException.class);
        verify(templateRepository, never()).save(any());
    }

    @Test
    void rejectsCreatingTemplateWithDeletedChannel() {
        NotificationEventType event = org.mockito.Mockito.mock(NotificationEventType.class);
        when(eventRepository.findById(1L)).thenReturn(Optional.of(event));
        when(channelRepository.findByIdAndDeletedFalse(2L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.createTemplate(
                new NotificationTemplateRequest(1L, 2L, "본문", 1)))
                .hasMessage("활성 채널을 찾을 수 없습니다.");
        verify(templateRepository, never()).save(any());
    }

    @Test
    void rejectsReusingCodeOfDeletedChannel() {
        ChannelType deletedChannel = org.mockito.Mockito.mock(ChannelType.class);
        when(deletedChannel.isDeleted()).thenReturn(true);
        when(channelRepository.findByCode("EMAIL")).thenReturn(Optional.of(deletedChannel));

        assertThatThrownBy(() -> service.createChannel(new ChannelTypeRequest("EMAIL", "이메일")))
                .isInstanceOf(ConflictException.class);
        verify(channelRepository, never()).save(any());
    }

    @Test
    void rejectsUpdatingChannelToCodeUsedByDeletedChannel() {
        ChannelType activeChannel = org.mockito.Mockito.mock(ChannelType.class);
        when(channelRepository.findByIdAndDeletedFalse(1L)).thenReturn(Optional.of(activeChannel));
        when(channelRepository.existsByCodeAndIdNot("EMAIL", 1L)).thenReturn(true);

        assertThatThrownBy(() -> service.updateChannel(1L, new ChannelTypeRequest("EMAIL", "이메일")))
                .isInstanceOf(ConflictException.class);
        verify(activeChannel, never()).changeDetails(any(), any());
    }
}
