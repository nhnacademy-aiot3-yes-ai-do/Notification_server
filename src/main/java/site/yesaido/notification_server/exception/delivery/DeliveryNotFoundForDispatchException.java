package site.yesaido.notification_server.exception.delivery;

import site.yesaido.common.exception.client.NotFoundException;

public class DeliveryNotFoundForDispatchException extends NotFoundException {
    private static final String DEFAULT_MESSAGE = "발송 대상 이력을 찾을 수 없습니다";

    public DeliveryNotFoundForDispatchException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content));
    }
}
