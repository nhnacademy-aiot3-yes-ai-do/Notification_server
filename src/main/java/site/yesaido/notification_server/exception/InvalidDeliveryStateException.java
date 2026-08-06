package site.yesaido.notification_server.exception;

public class InvalidDeliveryStateException extends IllegalStateException {

    public InvalidDeliveryStateException(String message) {
        super(message);
    }
}
