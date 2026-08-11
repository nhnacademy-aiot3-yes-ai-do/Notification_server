package site.yesaido.notification_server.exception.endpoint;

import site.yesaido.notification_server.exception.basic.client.NotFoundException;

public class NotificationChannelNotFoundException extends NotFoundException {
    private static final String DEFAULT_MESSAGE = "알림 채널을 찾을 수 없습니다";

    public NotificationChannelNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
