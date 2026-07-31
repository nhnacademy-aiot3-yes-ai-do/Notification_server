package site.yesaido.notification_server.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NotificationSubscriptionTest {

    private final NotificationSubscriptionType subscriptionType = createSubscriptionType();
    private final NotificationEndpoint endpoint = new NotificationEndpoint(
            7L, new ChannelType("DISCORD", "Discord"), "https://discord.test/webhook", "내 Discord");

    @Test
    void 생성하면_활성_상태이고_삭제되지_않는다() {
        NotificationSubscription subscription = new NotificationSubscription(
                subscriptionType, endpoint, 12L);

        assertTrue(subscription.isEnabled());
        assertFalse(subscription.isDeleted());
    }

    @Test
    void 구독을_비활성화하고_다시_활성화할_수_있다() {
        NotificationSubscription subscription = new NotificationSubscription(
                subscriptionType, endpoint, 12L);

        subscription.changeEnabled(false);
        assertFalse(subscription.isEnabled());

        subscription.changeEnabled(true);
        assertTrue(subscription.isEnabled());
    }

    @Test
    void 삭제하면_비활성화되고_삭제상태가_된다() {
        NotificationSubscription subscription = new NotificationSubscription(
                subscriptionType, endpoint, 12L);

        subscription.softDelete();

        assertFalse(subscription.isEnabled());
        assertTrue(subscription.isDeleted());
    }

    @Test
    void 삭제된_구독은_다시_활성화할_수_없다() {
        NotificationSubscription subscription = new NotificationSubscription(
                subscriptionType, endpoint, 12L);

        subscription.softDelete();
        subscription.changeEnabled(true);

        assertFalse(subscription.isEnabled());
        assertTrue(subscription.isDeleted());
    }

    private NotificationSubscriptionType createSubscriptionType() {
        SubscriptionTargetType targetType = new SubscriptionTargetType("CULTIVATION", "재배");
        NotificationEventType eventType = new NotificationEventType(
                "SENSOR_ERROR", "센서 오류", "센서 오류 알림", targetType);
        return new NotificationSubscriptionType(
                eventType, targetType, "센서 오류 알림", "센서 오류가 발생하면 알림을 보냅니다.");
    }
}
