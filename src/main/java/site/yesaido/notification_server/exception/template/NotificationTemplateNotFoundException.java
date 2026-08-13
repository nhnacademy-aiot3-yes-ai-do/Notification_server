package site.yesaido.notification_server.exception.template;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

public class NotificationTemplateNotFoundException extends CustomServerException {
    private static final String DEFAULT_MESSAGE = "이벤트와 채널에 맞는 알림 템플릿을 찾을 수 없습니다";

    public NotificationTemplateNotFoundException(String content) {
        super(DEFAULT_MESSAGE, "%s - %s".formatted(DEFAULT_MESSAGE, content), ServerErrorLevel.ERROR_LEVEL);
    }
}
