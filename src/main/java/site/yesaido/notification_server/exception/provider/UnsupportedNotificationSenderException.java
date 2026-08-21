package site.yesaido.notification_server.exception.provider;

import site.yesaido.common.exception.client.BadRequestException;

public class UnsupportedNotificationSenderException extends BadRequestException {
    private static final String DEFAULT_MESSAGE = "지원하지 않는 알림 발송자입니다";

    public UnsupportedNotificationSenderException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
