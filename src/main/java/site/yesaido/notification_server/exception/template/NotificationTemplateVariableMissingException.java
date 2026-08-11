package site.yesaido.notification_server.exception.template;

import site.yesaido.notification_server.exception.basic.server.CustomServerException;
import site.yesaido.notification_server.exception.basic.server.ServerErrorLevel;

public class NotificationTemplateVariableMissingException extends CustomServerException {
    private static final String DEFAULT_MESSAGE = "알림 템플릿에 필요한 변수가 없습니다";

    public NotificationTemplateVariableMissingException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content), ServerErrorLevel.ERROR_LEVEL);
    }
}
