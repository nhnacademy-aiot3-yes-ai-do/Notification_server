package site.yesaido.notification_server.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.DeliveryStatus;
import site.yesaido.notification_server.entity.Notification;
import site.yesaido.notification_server.entity.NotificationDelivery;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.entity.NotificationSubscription;
import site.yesaido.notification_server.entity.NotificationSubscriptionType;
import site.yesaido.notification_server.entity.NotificationTemplate;
import site.yesaido.notification_server.entity.SubscriptionTargetType;
import site.yesaido.notification_server.exception.delivery.DeliveryNotFoundForDispatchException;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;

@ExtendWith(MockitoExtension.class)
class DeliveryStateServiceTest {

    @Mock
    private NotificationDeliveryRepository deliveryRepository;

    private DeliveryStateService service;

    @BeforeEach
    void setUp() {
        service = new DeliveryStateService(deliveryRepository);
    }

    @Test
    void 선점에_실패하면_발송명령을_만들지_않는다() {
        when(deliveryRepository.claimPendingDelivery(7L, NotificationDelivery.MAX_ATTEMPT_COUNT))
                .thenReturn(0);

        assertTrue(service.claimForDispatch(7L).isEmpty());
    }

    @Test
    void 선점에_성공하면_Provider_호출에_필요한_발송명령을_만든다() {
        NotificationDelivery delivery = sendingDelivery(7L);
        delivery.increaseAttemptCount();
        when(deliveryRepository.claimPendingDelivery(7L, NotificationDelivery.MAX_ATTEMPT_COUNT))
                .thenReturn(1);
        when(deliveryRepository.findById(7L)).thenReturn(Optional.of(delivery));

        DeliveryCommand command = service.claimForDispatch(7L).orElseThrow();

        assertEquals(7L, command.deliveryId());
        assertEquals("DISCORD", command.channelCode());
        assertEquals("https://discord.com/api/webhooks/1/token", command.destination());
        assertEquals("임계값 초과", command.message());
        assertEquals(1, command.attemptCount());
    }

    @Test
    void 선점후_행을_찾을수_없으면_전용예외를_던진다() {
        when(deliveryRepository.claimPendingDelivery(7L, NotificationDelivery.MAX_ATTEMPT_COUNT))
                .thenReturn(1);
        when(deliveryRepository.findById(7L)).thenReturn(Optional.empty());

        assertThrows(DeliveryNotFoundForDispatchException.class,
                () -> service.claimForDispatch(7L));
    }

    @Test
    void 발송시도와_성공상태를_도메인메서드로_기록한다() {
        NotificationDelivery delivery = sendingDelivery(7L);
        when(deliveryRepository.findById(7L)).thenReturn(Optional.of(delivery));

        service.recordAttempt(7L);
        service.markSent(7L, "discord-message-1");

        assertEquals(1, delivery.getAttemptCount());
        assertEquals(DeliveryStatus.SENT, delivery.getStatus());
        assertEquals("discord-message-1", delivery.getProviderMessageId());
    }

    @Test
    void null이나_공백인_실패원인은_기본문구로_정규화한다() {
        NotificationDelivery nullErrorDelivery = sendingDelivery(7L);
        NotificationDelivery blankErrorDelivery = sendingDelivery(8L);
        when(deliveryRepository.findById(7L)).thenReturn(Optional.of(nullErrorDelivery));
        when(deliveryRepository.findById(8L)).thenReturn(Optional.of(blankErrorDelivery));

        service.markFailed(7L, null);
        service.markFailed(8L, "   ");

        assertEquals("알 수 없는 발송 오류", nullErrorDelivery.getError());
        assertEquals("알 수 없는 발송 오류", blankErrorDelivery.getError());
    }

    @Test
    void 긴_실패원인은_DB_허용길이로_잘라서_기록한다() {
        NotificationDelivery delivery = sendingDelivery(7L);
        when(deliveryRepository.findById(7L)).thenReturn(Optional.of(delivery));

        service.markFailed(7L, "가".repeat(1001));

        assertEquals(1000, delivery.getError().length());
    }

    @Test
    void 정상_실패원인은_그대로_기록한다() {
        NotificationDelivery delivery = sendingDelivery(7L);
        when(deliveryRepository.findById(7L)).thenReturn(Optional.of(delivery));

        service.markFailed(7L, "provider timeout");

        assertEquals("provider timeout", delivery.getError());
    }

    @Test
    void 오래된_선점은_Repository_갱신건수로_복구여부를_판단한다() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        when(deliveryRepository.releaseStaleSendingClaim(7L, staleBefore)).thenReturn(1);
        when(deliveryRepository.releaseStaleSendingClaim(8L, staleBefore)).thenReturn(0);

        assertTrue(service.releaseStaleClaim(7L, staleBefore));
        assertFalse(service.releaseStaleClaim(8L, staleBefore));
    }

    @Test
    void 시도횟수를_소진한_오래된_선점은_실패로_마감한다() {
        LocalDateTime staleBefore = LocalDateTime.now().minusMinutes(5);
        when(deliveryRepository.failStaleSendingDeliveryWhenAttemptsExhausted(
                7L, staleBefore, "알 수 없는 발송 오류", NotificationDelivery.MAX_ATTEMPT_COUNT))
                .thenReturn(1);
        when(deliveryRepository.failStaleSendingDeliveryWhenAttemptsExhausted(
                8L, staleBefore, "timeout", NotificationDelivery.MAX_ATTEMPT_COUNT))
                .thenReturn(0);

        assertTrue(service.failStaleClaimWhenAttemptsExhausted(7L, staleBefore, null));
        assertFalse(service.failStaleClaimWhenAttemptsExhausted(8L, staleBefore, "timeout"));
        verify(deliveryRepository).failStaleSendingDeliveryWhenAttemptsExhausted(
                7L, staleBefore, "알 수 없는 발송 오류", NotificationDelivery.MAX_ATTEMPT_COUNT);
    }

    private NotificationDelivery sendingDelivery(Long id) {
        SubscriptionTargetType targetType = new SubscriptionTargetType("CULTIVATION", "재배");
        NotificationEventType eventType = new NotificationEventType(
                "ENVIRONMENT_THRESHOLD_BREACHED", "임계값 초과", "임계값 초과", targetType);
        ChannelType channelType = new ChannelType("DISCORD", "Discord");
        NotificationSubscriptionType subscriptionType = new NotificationSubscriptionType(
                eventType, targetType, "임계값 초과", "환경 임계값 초과 알림");
        NotificationEndpoint endpoint = new NotificationEndpoint(
                20L, channelType, "https://discord.com/api/webhooks/1/token", "테스트 Discord");
        NotificationSubscription subscription = new NotificationSubscription(
                subscriptionType, endpoint, 3L);
        Notification notification = new Notification(UUID.randomUUID(), eventType, Map.of());
        NotificationTemplate template = new NotificationTemplate(
                eventType, channelType, "임계값 초과", 1);
        NotificationDelivery delivery = new NotificationDelivery(
                notification, subscription, template, "임계값 초과");
        ReflectionTestUtils.setField(delivery, "id", id);
        delivery.claimForDispatch();
        return delivery;
    }
}
