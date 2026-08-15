package site.yesaido.notification_server.rabbitmq.exception;


import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

/** RabbitMQ fan-out 실패 이력 생성에 전달된 시도 횟수가 정책 범위를 벗어난 경우다. */
public class RabbitMqFanoutAttemptCountInvalidException extends CustomServerException {
    private static final String DEFAULT_MESSAGE = "RabbitMQ fan-out 실패 시도 횟수가 유효하지 않습니다.";

    public RabbitMqFanoutAttemptCountInvalidException(short attemptCount, short maxAttemptCount) {
        super(
                DEFAULT_MESSAGE,
                "%s - attemptCount=%d, maxAttemptCount=%d".formatted(
                        DEFAULT_MESSAGE, attemptCount, maxAttemptCount),
                ServerErrorLevel.ERROR_LEVEL);
    }
}
