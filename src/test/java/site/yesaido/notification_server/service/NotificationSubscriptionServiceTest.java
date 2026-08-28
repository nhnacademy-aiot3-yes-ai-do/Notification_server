package site.yesaido.notification_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.entity.NotificationSubscription;
import site.yesaido.notification_server.entity.NotificationSubscriptionType;
import site.yesaido.notification_server.entity.SubscriptionTargetType;
import site.yesaido.notification_server.client.SubscriptionTargetAccessClient;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.exception.subscription.NotificationSubscriptionNotFoundException;
import site.yesaido.notification_server.exception.subscription.SubscriptionCreationEndpointNotFoundException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessDeniedException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetAccessUnverifiedException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetNotFoundException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTypeNotFoundException;
import site.yesaido.notification_server.exception.subscription.UnsupportedSubscriptionChannelException;
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
    private final SubscriptionTargetAccessClient targetAccessClient =
            mock(SubscriptionTargetAccessClient.class);
    private NotificationSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new NotificationSubscriptionService(
                subscriptionRepository, typeRepository, endpointRepository, channelRepository,
                targetAccessClient);
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
        verify(targetAccessClient).requireCultivationAccess(7L, 101L);
    }

    @Test
    void rejectsChannelNotSupportedBySubscriptionType() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L);
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(22L, 11L, 101L);

        assertThatThrownBy(() -> service.create(
                7L, request))
                .isInstanceOf(UnsupportedSubscriptionChannelException.class);
    }

    @Test
    void rejectsAnotherUserAsUserTarget() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L, "USER");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(22L, 11L, 8L);

        assertThatThrownBy(() -> service.create(
                7L, request))
                .isInstanceOf(SubscriptionTargetNotFoundException.class);
        verify(targetAccessClient, never()).requireCultivationAccess(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
        verify(targetAccessClient, never()).requireInquiryAccess(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 소유한_Endpoint가_없으면_구독을_만들수_없다() {
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.empty());
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(22L, 11L, 101L);

        assertThatThrownBy(() -> service.create(
                7L, request))
                .isInstanceOf(SubscriptionCreationEndpointNotFoundException.class);

        verify(typeRepository, never()).findById(22L);
    }

    @Test
    void 존재하지_않는_구독종류로_구독을_만들수_없다() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.empty());
        SubscriptionCreateRequest request = new SubscriptionCreateRequest(22L, 11L, 101L);

        assertThatThrownBy(() -> service.create(
                7L, request))
                .isInstanceOf(SubscriptionTypeNotFoundException.class);
    }

    @Test
    void 지원채널이고_기존구독이_없으면_새_구독을_저장한다() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L);
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));
        when(channelRepository.existsBySubscriptionType_IdAndChannelType_Id(22L, 1L))
                .thenReturn(true);
        when(subscriptionRepository.findBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndDeletedFalse(
                22L, 11L, 101L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(org.mockito.ArgumentMatchers.any(NotificationSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(7L, new SubscriptionCreateRequest(22L, 11L, 101L));

        assertThat(response.subscriptionTypeId()).isEqualTo(22L);
        assertThat(response.targetId()).isEqualTo(101L);
        assertThat(response.enabled()).isTrue();
        verify(targetAccessClient).requireCultivationAccess(7L, 101L);
    }

    @Test
    void 재배지_권한이_없으면_구독을_만들수_없다() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L, "CULTIVATION");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));
        doThrow(new SubscriptionTargetAccessDeniedException("cultivation id:101"))
                .when(targetAccessClient).requireCultivationAccess(7L, 101L);

        assertThatThrownBy(() -> service.create(7L, new SubscriptionCreateRequest(22L, 11L, 101L)))
                .isInstanceOf(SubscriptionTargetAccessDeniedException.class);
        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 재배지_권한확인이_실패하면_구독을_만들지_않는다() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L, "CULTIVATION");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));
        doThrow(new SubscriptionTargetAccessUnverifiedException("timeout"))
                .when(targetAccessClient).requireCultivationAccess(7L, 101L);

        assertThatThrownBy(() -> service.create(7L, new SubscriptionCreateRequest(22L, 11L, 101L)))
                .isInstanceOf(SubscriptionTargetAccessUnverifiedException.class);
        verify(subscriptionRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void 문의_권한이_있으면_구독을_만들수_있다() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L, "INQUIRY");
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));
        when(channelRepository.existsBySubscriptionType_IdAndChannelType_Id(22L, 1L))
                .thenReturn(true);
        when(subscriptionRepository.findBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndDeletedFalse(
                22L, 11L, 55L)).thenReturn(Optional.empty());
        when(subscriptionRepository.save(org.mockito.ArgumentMatchers.any(NotificationSubscription.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        var response = service.create(7L, new SubscriptionCreateRequest(22L, 11L, 55L));

        assertThat(response.targetId()).isEqualTo(55L);
        verify(targetAccessClient).requireInquiryAccess(7L, 55L);
    }

    @Test
    void USER_대상은_본인_ID일때_구독할수_있다() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L, "USER");
        NotificationSubscription subscription = subscription(31L, type, endpoint, 7L);
        when(endpointRepository.findByIdAndUserIdAndDeletedFalse(11L, 7L))
                .thenReturn(Optional.of(endpoint));
        when(typeRepository.findById(22L)).thenReturn(Optional.of(type));
        when(channelRepository.existsBySubscriptionType_IdAndChannelType_Id(22L, 1L))
                .thenReturn(true);
        when(subscriptionRepository.findBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndDeletedFalse(
                22L, 11L, 7L)).thenReturn(Optional.of(subscription));

        assertThat(service.create(7L, new SubscriptionCreateRequest(22L, 11L, 7L)).targetId())
                .isEqualTo(7L);
        verify(targetAccessClient, never()).requireInquiryAccess(org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void 활성_구독과_구독종류_목록을_응답으로_변환한다() {
        NotificationEndpoint endpoint = endpoint(11L, 1L, "TELEGRAM");
        NotificationSubscriptionType type = subscriptionType(22L);
        NotificationSubscription subscription = subscription(31L, type, endpoint, 101L);
        when(subscriptionRepository.findAllActiveByUserId(7L)).thenReturn(List.of(subscription));
        when(typeRepository.findAllWithEventAndTargetType()).thenReturn(List.of(type));

        assertThat(service.findAll(7L)).singleElement()
                .satisfies(response -> assertThat(response.id()).isEqualTo(31L));
        assertThat(service.findTypes()).singleElement()
                .satisfies(response -> assertThat(response.id()).isEqualTo(22L));
    }

    @Test
    void 소유한_구독의_활성상태를_변경하고_삭제한다() {
        NotificationSubscription subscription = subscription(
                31L, subscriptionType(22L), endpoint(11L, 1L, "TELEGRAM"), 101L);
        when(subscriptionRepository.findByIdAndEndpoint_UserIdAndDeletedFalse(31L, 7L))
                .thenReturn(Optional.of(subscription));

        service.changeEnabled(7L, 31L, false);
        service.delete(7L, 31L);

        verify(subscription).changeEnabled(false);
        verify(subscription).softDelete();
    }

    @Test
    void 소유하지_않은_구독은_변경할수_없다() {
        when(subscriptionRepository.findByIdAndEndpoint_UserIdAndDeletedFalse(31L, 7L))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.changeEnabled(7L, 31L, false))
                .isInstanceOf(NotificationSubscriptionNotFoundException.class);
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

    private NotificationSubscription subscription(
            Long id, NotificationSubscriptionType type, NotificationEndpoint endpoint, Long targetId) {
        NotificationSubscription subscription = mock(NotificationSubscription.class);
        when(subscription.getId()).thenReturn(id);
        when(subscription.getSubscriptionType()).thenReturn(type);
        when(subscription.getEndpoint()).thenReturn(endpoint);
        when(subscription.getTargetId()).thenReturn(targetId);
        when(subscription.isEnabled()).thenReturn(true);
        return subscription;
    }
}
