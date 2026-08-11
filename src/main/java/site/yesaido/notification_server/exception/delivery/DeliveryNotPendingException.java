package site.yesaido.notification_server.exception.delivery;

import site.yesaido.notification_server.exception.basic.server.CustomServerException;
import site.yesaido.notification_server.exception.basic.server.ServerErrorLevel;

public class DeliveryNotPendingException extends CustomServerException {
    public DeliveryNotPendingException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }
}
