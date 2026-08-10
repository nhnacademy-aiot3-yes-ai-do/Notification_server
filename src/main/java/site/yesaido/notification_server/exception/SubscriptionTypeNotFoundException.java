package site.yesaido.notification_server.exception;

public class SubscriptionTypeNotFoundException extends NotificationApiException {

    public SubscriptionTypeNotFoundException() {
        super(NotificationErrorCode.SUBSCRIPTION_TYPE_NOT_FOUND);
    }
}
