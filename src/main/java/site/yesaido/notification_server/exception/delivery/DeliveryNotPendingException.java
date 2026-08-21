package site.yesaido.notification_server.exception.delivery;

import site.yesaido.common.exception.server.CustomServerException;
import site.yesaido.common.exception.server.ServerErrorLevel;

public class DeliveryNotPendingException extends CustomServerException {
    public DeliveryNotPendingException(String message) {
        super(message, ServerErrorLevel.ERROR_LEVEL);
    }
}
