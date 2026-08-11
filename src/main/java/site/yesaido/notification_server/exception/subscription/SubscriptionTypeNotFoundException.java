package site.yesaido.notification_server.exception.subscription;

import site.yesaido.notification_server.exception.basic.client.NotFoundException;

public class SubscriptionTypeNotFoundException extends NotFoundException {
    private static final String DEFAULT_MESSAGE = "알림 구독 종류를 찾을 수 없습니다";

    public SubscriptionTypeNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
