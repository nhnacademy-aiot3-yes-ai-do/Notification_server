package site.yesaido.notification_server.messaging;

/**
 * RabbitMQ에서 받은 문자열을 유효한 공통 이벤트로 변환할 수 없을 때 발생한다.
 */
public class InvalidDomainEventException extends RuntimeException {

    public InvalidDomainEventException(String message) {
        super(message);
    }

    public InvalidDomainEventException(String message, Throwable cause) {
        super(message, cause);
    }
}
