package site.yesaido.notification_server.rabbitmq.exception;


import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

/** 코드의 event type 계약과 DB 기준 데이터가 불일치한 서버 구성 오류다. */
public class NotificationEventTargetTypeMismatchException extends CustomServerException {
    private static final String DEFAULT_MESSAGE = "이벤트 대상 유형이 기준 정보와 일치하지 않습니다";

    public NotificationEventTargetTypeMismatchException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content), ServerErrorLevel.ERROR_LEVEL);
    }
}
