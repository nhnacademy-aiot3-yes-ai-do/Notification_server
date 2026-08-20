package site.yesaido.notification_server.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.entity.*;
import site.yesaido.notification_server.exception.delivery.DeliveryAttemptLimitExceededException;
import site.yesaido.notification_server.exception.delivery.DeliveryNotPendingException;
import site.yesaido.notification_server.exception.delivery.DeliveryNotSendingException;
import site.yesaido.notification_server.rabbitmq.exception.RabbitMqFanoutAttemptCountInvalidException;

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
        Notification notification = new Notification(UUID.randomUUID(), eventType, Map.of("sensorId", 3));
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
    void RabbitMQ_fanout_최종실패는_template이_없어도_실패이력으로_생성된다() {
        NotificationDelivery delivery = NotificationDelivery.failForRabbitMqFanout(
                createDelivery().getNotification(), createDelivery().getSubscription(), null,
                (short) 2, "template not found");

        assertEquals(DeliveryStatus.FAILED, delivery.getStatus());
        assertEquals(2, delivery.getAttemptCount());
        assertEquals("template not found", delivery.getError());
        assertEquals("", delivery.getRenderedMessage());
        assertEquals(null, delivery.getTemplate());
    }

    @Test
    void RabbitMQ_fanout_실패시도횟수가_범위를_벗어나면_전용예외를_던진다() {
        NotificationDelivery source = createDelivery();

        assertThrows(
                site.yesaido.notification_server.rabbitmq.exception.RabbitMqFanoutAttemptCountInvalidException.class,
                () -> NotificationDelivery.failForRabbitMqFanout(
                        source.getNotification(), source.getSubscription(), null,
                        (short) 0, "template not found"));
    }

    @Test
    void 성공한_발송은_실패상태로_되돌릴_수_없다() {
        NotificationDelivery delivery = createDelivery();
        delivery.claimForDispatch();
        delivery.markSent("telegram-message-1");

        assertThrows(DeliveryNotSendingException.class,
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
        assertThrows(DeliveryAttemptLimitExceededException.class, delivery::increaseAttemptCount);
    }

    @Test
    void 발송은_대기상태에서_한번만_선점할_수_있다() {
        NotificationDelivery delivery = createDelivery();

        delivery.claimForDispatch();

        assertEquals(DeliveryStatus.SENDING, delivery.getStatus());
        assertThrows(DeliveryNotPendingException.class, delivery::claimForDispatch);
    }

    @Test
    void CREATED_발송은_활성화하면_PENDING으로_전환된다() {
        NotificationDelivery source = createDelivery();
        NotificationDelivery delivery = NotificationDelivery.prepare(
                source.getNotification(), source.getSubscription(), source.getTemplate(), "생성 메시지");

        assertEquals(DeliveryStatus.CREATED, delivery.getStatus());
        delivery.activateForDispatch();

        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertTrue(delivery.canRetry());
    }

    @Test
    void CREATED가_아닌_발송은_활성화할_수_없다() {
        NotificationDelivery delivery = createDelivery();

        assertThrows(IllegalStateException.class, delivery::activateForDispatch);
    }

    @Test
    void SENDING_발송은_stale_복구시_PENDING으로_돌아온다() {
        NotificationDelivery delivery = createDelivery();
        delivery.claimForDispatch();

        delivery.releaseStaleClaim();

        assertEquals(DeliveryStatus.PENDING, delivery.getStatus());
        assertTrue(delivery.canRetry());
    }

    @Test
    void RabbitMQ_실패시도횟수의_상한을_초과하면_전용예외를_던진다() {
        NotificationDelivery source = createDelivery();
        Notification notification = source.getNotification();
        NotificationSubscription subscription = source.getSubscription();

        assertThrows(RabbitMqFanoutAttemptCountInvalidException.class,
                () -> NotificationDelivery.failForRabbitMqFanout(
                        notification, subscription, null,
                        (short) 4, "too many attempts"));
    }

    @Test
    void SENDING이거나_최대시도전인_발송은_재시도할_수_있다() {
        NotificationDelivery pending = createDelivery();
        NotificationDelivery sending = createDelivery();
        sending.claimForDispatch();

        assertTrue(pending.canRetry());
        assertTrue(sending.canRetry());
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
        Notification notification = new Notification(UUID.randomUUID(), eventType, Map.of("sensorId", 3));
        NotificationTemplate template = new NotificationTemplate(eventType, channelType, "센서 오류", 1);
        return new NotificationDelivery(notification, subscription, template, "센서 오류");
    }
}
