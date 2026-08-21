package site.yesaido.notification_server.rabbitmq.exception;


import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

/** 코드가 발생시킨 event type을 DB 기준 데이터에서 찾을 수 없는 서버 구성 오류다. */
public class NotificationEventTypeNotFoundException extends CustomServerException {
    private static final String DEFAULT_MESSAGE = "등록되지 않은 알림 이벤트 유형입니다";

    public NotificationEventTypeNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content), ServerErrorLevel.ERROR_LEVEL);
    }
}
