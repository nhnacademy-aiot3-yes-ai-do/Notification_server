package site.yesaido.notification_server.exception.subscription;

import site.yesaido.common.exception.client.NotFoundException;

public class SubscriptionTargetNotFoundException extends NotFoundException {
    private static final String DEFAULT_MESSAGE = "알림 대상 사용자를 찾을 수 없습니다";

    public SubscriptionTargetNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
