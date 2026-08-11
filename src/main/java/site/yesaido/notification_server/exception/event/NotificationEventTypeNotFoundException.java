package site.yesaido.notification_server.exception.event;

import site.yesaido.notification_server.exception.basic.client.BadRequestException;

public class NotificationEventTypeNotFoundException extends BadRequestException {
    private static final String DEFAULT_MESSAGE = "등록되지 않은 알림 이벤트 유형입니다";

    public NotificationEventTypeNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
