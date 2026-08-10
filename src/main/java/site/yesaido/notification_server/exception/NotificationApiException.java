package site.yesaido.notification_server.exception;

public abstract class NotificationApiException extends RuntimeException {

    private final NotificationErrorCode errorCode;

    protected NotificationApiException(NotificationErrorCode errorCode) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public NotificationErrorCode getErrorCode() {
        return errorCode;
    }
}
