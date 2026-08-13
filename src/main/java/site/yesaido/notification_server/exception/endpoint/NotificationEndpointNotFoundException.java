package site.yesaido.notification_server.exception.endpoint;

import site.yesaido.common.exception.client.NotFoundException;

public class NotificationEndpointNotFoundException extends NotFoundException {
    private static final String DEFAULT_MESSAGE = "알림 수신 경로를 찾을 수 없습니다";

    public NotificationEndpointNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
