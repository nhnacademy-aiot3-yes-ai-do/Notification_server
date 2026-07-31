package site.yesaido.notification_server.exception;

public class NotificationProviderException extends RuntimeException {

    public NotificationProviderException(String message) {
        super(message);
    }

    public NotificationProviderException(String message, Throwable cause) {
        super(message, cause);
    }
}
