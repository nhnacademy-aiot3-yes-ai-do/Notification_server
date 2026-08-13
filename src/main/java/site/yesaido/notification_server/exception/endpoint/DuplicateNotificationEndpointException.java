package site.yesaido.notification_server.exception.endpoint;

import site.yesaido.common.exception.client.ConflictException;

public class DuplicateNotificationEndpointException extends ConflictException {
    private static final String DEFAULT_MESSAGE = "이미 등록된 알림 수신 경로입니다";

    public DuplicateNotificationEndpointException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
