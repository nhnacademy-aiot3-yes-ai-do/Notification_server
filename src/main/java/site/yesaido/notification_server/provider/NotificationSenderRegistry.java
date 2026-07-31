package site.yesaido.notification_server.provider;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.exception.UnsupportedNotificationChannelException;

@Component
public class NotificationSenderRegistry {

    private final Map<String, NotificationSender> senders;

    public NotificationSenderRegistry(List<NotificationSender> senders) {
        this.senders = senders.stream()
                .collect(Collectors.toUnmodifiableMap(
                        sender -> sender.channelCode().toUpperCase(Locale.ROOT),
                        Function.identity()));
    }

    public NotificationSender get(String channelCode) {
        NotificationSender sender = senders.get(channelCode.toUpperCase(Locale.ROOT));
        if (sender == null) {
            throw new UnsupportedNotificationChannelException(
                    "지원하지 않는 알림 채널입니다: " + channelCode);
        }
        return sender;
    }
}
