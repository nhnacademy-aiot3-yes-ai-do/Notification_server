package com.ecosphere.notification.domain;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
        Notification notification = new Notification(
                UUID.randomUUID(), Map.of("sensorId", 3), "센서 오류가 발생했습니다.");
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
    void 발송_성공과_실패는_도메인_메서드로_상태를_변경한다() {
        NotificationDelivery delivery = createDelivery();

        delivery.increaseAttemptCount();
        delivery.markSent("telegram-message-1");

        assertEquals(DeliveryStatus.SENT, delivery.getStatus());
        assertEquals(1, delivery.getAttemptCount());
        assertEquals("telegram-message-1", delivery.getProviderMessageId());
        assertNotNull(delivery.getSentAt());

        delivery.markFailed("provider timeout");
        assertEquals(DeliveryStatus.FAILED, delivery.getStatus());
        assertEquals("provider timeout", delivery.getError());
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
        Notification notification = new Notification(UUID.randomUUID(), Map.of("sensorId", 3), "센서 오류");
        NotificationTemplate template = new NotificationTemplate(eventType, channelType, "센서 오류", 1);
        return new NotificationDelivery(notification, subscription, template, "센서 오류");
    }
}
