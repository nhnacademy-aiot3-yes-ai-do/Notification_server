package site.yesaido.notification_server.exception;

public class EndpointNotFoundException extends NotificationApiException {

    public EndpointNotFoundException() {
        super(NotificationErrorCode.ENDPOINT_NOT_FOUND);
    }
}
