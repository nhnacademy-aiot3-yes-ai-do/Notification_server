package site.yesaido.notification_server.exception.subscription;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

public class SubscriptionTargetAccessUnverifiedException extends CustomServerException {
    private static final String DEFAULT_MESSAGE = "알림 대상 권한을 확인할 수 없습니다";

    public SubscriptionTargetAccessUnverifiedException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content), ServerErrorLevel.WARN_LEVEL);
    }

    public SubscriptionTargetAccessUnverifiedException(String content, Throwable cause) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content), cause, ServerErrorLevel.WARN_LEVEL);
    }
}
