package site.yesaido.notification_server.domain;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEndpoint;

class NotificationEndpointTest {

    private final ChannelType telegram = new ChannelType("TELEGRAM", "Telegram");

    @Test
    void 생성하면_활성_상태이고_삭제되지_않는다() {
        NotificationEndpoint endpoint = new NotificationEndpoint(
                7L, telegram, "123456", "내 텔레그램");

        assertTrue(endpoint.isEnabled());
        assertFalse(endpoint.isDeleted());
    }

    @Test
    void 비활성화와_재활성화를_할_수_있다() {
        NotificationEndpoint endpoint = new NotificationEndpoint(
                7L, telegram, "123456", "내 텔레그램");

        endpoint.changeEnabled(false);
        assertFalse(endpoint.isEnabled());

        endpoint.changeEnabled(true);
        assertTrue(endpoint.isEnabled());
    }

    @Test
    void 삭제하면_비활성화되고_삭제상태가_된다() {
        NotificationEndpoint endpoint = new NotificationEndpoint(
                7L, telegram, "123456", "내 텔레그램");

        endpoint.softDelete();

        assertFalse(endpoint.isEnabled());
        assertTrue(endpoint.isDeleted());
    }

    @Test
    void 삭제된_endpoint는_다시_활성화할_수_없다() {
        NotificationEndpoint endpoint = new NotificationEndpoint(
                7L, telegram, "123456", "내 텔레그램");

        endpoint.softDelete();
        endpoint.changeEnabled(true);

        assertFalse(endpoint.isEnabled());
        assertTrue(endpoint.isDeleted());
    }

    @Test
    void endpoint_정보를_수정할_수_있다() {
        NotificationEndpoint endpoint = new NotificationEndpoint(
                7L, telegram, "123456", "기존 이름");

        endpoint.update("654321", "새 이름");

        org.junit.jupiter.api.Assertions.assertEquals("654321", endpoint.getDestination());
        org.junit.jupiter.api.Assertions.assertEquals("새 이름", endpoint.getDisplayName());
    }
}
