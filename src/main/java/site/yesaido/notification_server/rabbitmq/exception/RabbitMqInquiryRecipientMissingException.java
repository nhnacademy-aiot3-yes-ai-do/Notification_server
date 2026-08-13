package site.yesaido.notification_server.rabbitmq.exception;

import site.yesaido.notification_server.exception.basic.server.CustomServerException;
import site.yesaido.notification_server.exception.basic.server.ServerErrorLevel;

/** 문의 RabbitMQ 이벤트에 실제 알림 수신자가 없는 producer 계약 위반이다. */
public class RabbitMqInquiryRecipientMissingException extends CustomServerException {
    private static final String DEFAULT_MESSAGE = "문의 이벤트에 알림 수신자가 없습니다.";

    public RabbitMqInquiryRecipientMissingException(Long inquiryId) {
        super(
                DEFAULT_MESSAGE,
                "%s - inquiryId=%d".formatted(DEFAULT_MESSAGE, inquiryId),
                ServerErrorLevel.ERROR_LEVEL);
    }
}
