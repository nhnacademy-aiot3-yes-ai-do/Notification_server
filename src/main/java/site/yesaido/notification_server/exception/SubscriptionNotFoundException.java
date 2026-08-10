package site.yesaido.notification_server.exception;

public class SubscriptionNotFoundException extends NotificationApiException {

    public SubscriptionNotFoundException() {
        super(NotificationErrorCode.SUBSCRIPTION_NOT_FOUND);
    }
}
