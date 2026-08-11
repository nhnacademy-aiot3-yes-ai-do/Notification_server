package site.yesaido.notification_server.exception.subscription;

import site.yesaido.notification_server.exception.basic.client.NotFoundException;

public class NotificationSubscriptionNotFoundException extends NotFoundException {
    private static final String DEFAULT_MESSAGE = "알림 구독을 찾을 수 없습니다";

    public NotificationSubscriptionNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
