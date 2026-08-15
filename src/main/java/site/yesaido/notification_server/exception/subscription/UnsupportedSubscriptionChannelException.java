package site.yesaido.notification_server.exception.subscription;

import site.yesaido.common.exception.client.BadRequestException;

public class UnsupportedSubscriptionChannelException extends BadRequestException {
    private static final String DEFAULT_MESSAGE = "해당 구독에서 지원하지 않는 알림 채널입니다";

    public UnsupportedSubscriptionChannelException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
