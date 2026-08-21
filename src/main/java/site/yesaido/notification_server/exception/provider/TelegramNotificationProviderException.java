package site.yesaido.notification_server.exception.provider;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

public class TelegramNotificationProviderException extends CustomServerException {
    public TelegramNotificationProviderException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }

    public TelegramNotificationProviderException(String message, Throwable cause) {
        super(message, cause, ServerErrorLevel.ERROR_LEVEL);
    }
}
