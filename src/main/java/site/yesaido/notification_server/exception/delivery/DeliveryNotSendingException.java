package site.yesaido.notification_server.exception.delivery;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

public class DeliveryNotSendingException extends CustomServerException {
    public DeliveryNotSendingException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }
}
