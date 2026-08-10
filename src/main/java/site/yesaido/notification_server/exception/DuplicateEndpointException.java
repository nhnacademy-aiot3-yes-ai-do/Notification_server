package site.yesaido.notification_server.exception;

public class DuplicateEndpointException extends NotificationApiException {

    public DuplicateEndpointException() {
        super(NotificationErrorCode.ENDPOINT_ALREADY_EXISTS);
    }
}
