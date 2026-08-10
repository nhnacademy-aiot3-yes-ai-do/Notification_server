package site.yesaido.notification_server.exception.subscription;

import site.yesaido.notification_server.exception.basic.client.NotFoundException;

public class SubscriptionCreationEndpointNotFoundException extends NotFoundException {
    private static final String DEFAULT_MESSAGE = "구독 생성에 사용할 알림 수신 경로를 찾을 수 없습니다";

    public SubscriptionCreationEndpointNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
