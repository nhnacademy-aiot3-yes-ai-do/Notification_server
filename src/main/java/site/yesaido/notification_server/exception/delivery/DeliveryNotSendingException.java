package site.yesaido.notification_server.exception.delivery;

import site.yesaido.notification_server.exception.basic.server.CustomServerException;
import site.yesaido.notification_server.exception.basic.server.ServerErrorLevel;

public class DeliveryNotSendingException extends CustomServerException {
    public DeliveryNotSendingException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }
}
