package site.yesaido.notification_server.exception.subscription;

import site.yesaido.common.exception.client.NotFoundException;

public class SubscriptionTargetAccessDeniedException extends NotFoundException {
    private static final String DEFAULT_MESSAGE = "알림 대상에 접근할 수 없습니다";

    public SubscriptionTargetAccessDeniedException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
