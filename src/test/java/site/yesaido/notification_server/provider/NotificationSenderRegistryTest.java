package site.yesaido.notification_server.provider;

import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.exception.provider.UnsupportedNotificationSenderException;

class NotificationSenderRegistryTest {

    @Test
    void 채널코드는_대소문자와_관계없이_같은_Sender를_찾는다() {
        NotificationSender discord = sender("discord");
        NotificationSenderRegistry registry = new NotificationSenderRegistry(List.of(discord));

        assertSame(discord, registry.get("DISCORD"));
        assertSame(discord, registry.get("discord"));
    }

    @Test
    void 지원하지_않는_채널은_전용예외로_거절한다() {
        NotificationSenderRegistry registry = new NotificationSenderRegistry(List.of(sender("DISCORD")));

        assertThrows(UnsupportedNotificationSenderException.class,
                () -> registry.get("EMAIL"));
    }

    @Test
    void 같은_채널의_Sender가_둘이면_애플리케이션_구성을_거절한다() {
        NotificationSender first = sender("discord");
        NotificationSender second = sender("DISCORD");
        List<NotificationSender> duplicateSenders = List.of(first, second);

        assertThrows(IllegalStateException.class,
                () -> new NotificationSenderRegistry(duplicateSenders));
    }

    private NotificationSender sender(String channelCode) {
        NotificationSender sender = mock(NotificationSender.class);
        when(sender.channelCode()).thenReturn(channelCode);
        return sender;
    }
}
