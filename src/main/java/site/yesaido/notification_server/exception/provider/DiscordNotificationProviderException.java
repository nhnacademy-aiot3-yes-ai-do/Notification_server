package site.yesaido.notification_server.exception.provider;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

public class DiscordNotificationProviderException extends CustomServerException {
    public DiscordNotificationProviderException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }

    public DiscordNotificationProviderException(String message, Throwable cause) {
        super(message, cause, ServerErrorLevel.ERROR_LEVEL);
    }
}
