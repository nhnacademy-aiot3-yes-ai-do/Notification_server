package site.yesaido.notification_server.exception.event;

import site.yesaido.notification_server.exception.basic.client.BadRequestException;

public class NotificationEventTargetTypeMismatchException extends BadRequestException {
    private static final String DEFAULT_MESSAGE = "이벤트 대상 유형이 기준 정보와 일치하지 않습니다";

    public NotificationEventTargetTypeMismatchException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
