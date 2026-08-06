package site.yesaido.notification_server.exception;

public class DuplicateNotificationResourceException extends RuntimeException {

    public DuplicateNotificationResourceException(String message) {
        super(message);
    }
}
