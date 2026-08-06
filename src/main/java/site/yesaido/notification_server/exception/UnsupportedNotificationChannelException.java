package site.yesaido.notification_server.exception;

public class UnsupportedNotificationChannelException extends RuntimeException {

    public UnsupportedNotificationChannelException(String message) {
        super(message);
    }
}
