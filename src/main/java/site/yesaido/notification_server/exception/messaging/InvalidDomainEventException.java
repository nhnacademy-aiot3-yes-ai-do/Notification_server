package site.yesaido.notification_server.exception.messaging;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

/**
 * RabbitMQ에서 받은 문자열을 유효한 공통 이벤트로 변환할 수 없을 때 발생한다.
 */
public class InvalidDomainEventException extends CustomServerException {

    public InvalidDomainEventException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }

    public InvalidDomainEventException(String message, Throwable cause) {
        super(message, cause, ServerErrorLevel.ERROR_LEVEL);
    }
}
