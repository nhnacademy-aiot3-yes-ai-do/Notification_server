package site.yesaido.notification_server.domain;

public class InvalidDeliveryStateException extends IllegalStateException {

    public InvalidDeliveryStateException(String message) {
        super(message);
    }
}
