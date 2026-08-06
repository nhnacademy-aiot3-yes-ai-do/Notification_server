package site.yesaido.notification_server.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.exception.InvalidDeliveryStateException;

class NotificationDeliveryTest {

    @Test
    void 발송_기록은_대기상태와_제로_시도로_생성된다() {
        SubscriptionTargetType targetType = new SubscriptionTargetType("CULTIVATION", "재배");
        NotificationEventType eventType = new NotificationEventType(
                "SENSOR_ERROR", "센서 오류", "센서 오류 알림", targetType);
        ChannelType channelType = new ChannelType("TELEGRAM", "Telegram");
        NotificationSubscriptionType subscriptionType = new NotificationSubscriptionType(
                eventType, targetType, "센서 오류 알림", "센서 오류가 발생하면 알림을 보냅니다.");
        NotificationEndpoint endpoint = new NotificationEndpoint(
                7L, channelType, "123456", "내 Telegram");
        NotificationSubscription subscription = new NotificationSubscription(
                subscriptionType, endpoint, 12L);
        Notification notification = new Notification(UUID.randomUUID(), Map.of("sensorId", 3));
        NotificationTemplate template = new NotificationTemplate(
                eventType, channelType, "센서 오류: {sensorId}", 1);

        NotificationDelivery delivery = new NotificationDelivery(
                notification, subscription, template, "센서 오류: 3");

        assertSame(notification, delivery.getNotification());
        assertSame(subscription, delivery.getSubscription());
        assertSame(template, delivery.getTemplate());
        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertEquals(0, delivery.getAttemptCount());
        assertEquals("센서 오류: 3", delivery.getRenderedMessage());
    }

    @Test
    void 발송에_성공하면_성공상태와_외부메시지ID를_기록한다() {
        NotificationDelivery delivery = createDelivery();

        delivery.claimForDispatch();
        delivery.increaseAttemptCount();
        delivery.markSent("telegram-message-1");

        assertEquals(DeliveryStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertEquals("telegram-message-1", delivery.getProviderMessageId());
        assertNotNull(delivery.getSentAt());
    }

    @Test
    void 최종_발송에_실패하면_실패상태와_원인을_기록한다() {
        NotificationDelivery delivery = createDelivery();

        delivery.claimForDispatch();
        delivery.markFailed("provider timeout");

        assertEquals(DeliveryStatus.FAILED, delivery.getStatus());
        assertEquals("provider timeout", delivery.getError());
    }

    @Test
    void 성공한_발송은_실패상태로_되돌릴_수_없다() {
        NotificationDelivery delivery = createDelivery();
        delivery.claimForDispatch();
        delivery.markSent("telegram-message-1");

        assertThrows(InvalidDeliveryStateException.class,
                () -> delivery.markFailed("late timeout"));
    }

    @Test
    void 발송_시도는_최대_세번까지만_기록한다() {
        NotificationDelivery delivery = createDelivery();

        delivery.claimForDispatch();
        delivery.increaseAttemptCount();
        delivery.increaseAttemptCount();
        delivery.increaseAttemptCount();

        assertEquals(3, delivery.getAttemptCount());
        assertFalse(delivery.canRetry());
        assertThrows(InvalidDeliveryStateException.class, delivery::increaseAttemptCount);
    }

    @Test
    void 발송은_대기상태에서_한번만_선점할_수_있다() {
        NotificationDelivery delivery = createDelivery();

        delivery.claimForDispatch();

        assertEquals(DeliveryStatus.SENDING, delivery.getStatus());
        assertThrows(InvalidDeliveryStateException.class, delivery::claimForDispatch);
    }

    private NotificationDelivery createDelivery() {
        SubscriptionTargetType targetType = new SubscriptionTargetType("CULTIVATION", "재배");
        NotificationEventType eventType = new NotificationEventType(
                "SENSOR_ERROR", "센서 오류", "센서 오류 알림", targetType);
        ChannelType channelType = new ChannelType("TELEGRAM", "Telegram");
        NotificationSubscriptionType subscriptionType = new NotificationSubscriptionType(
                eventType, targetType, "센서 오류 알림", "센서 오류가 발생하면 알림을 보냅니다.");
        NotificationEndpoint endpoint = new NotificationEndpoint(7L, channelType, "123456", "내 Telegram");
        NotificationSubscription subscription = new NotificationSubscription(subscriptionType, endpoint, 12L);
        Notification notification = new Notification(UUID.randomUUID(), Map.of("sensorId", 3));
        NotificationTemplate template = new NotificationTemplate(eventType, channelType, "센서 오류", 1);
        return new NotificationDelivery(notification, subscription, template, "센서 오류");
    }
}
