package site.yesaido.notification_server.exception;

public class NotificationTargetNotFoundException extends NotificationApiException {

    public NotificationTargetNotFoundException() {
        super(NotificationErrorCode.TARGET_NOT_FOUND);
    }
}
