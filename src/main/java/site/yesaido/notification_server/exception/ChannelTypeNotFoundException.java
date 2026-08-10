package site.yesaido.notification_server.exception;

public class ChannelTypeNotFoundException extends NotificationApiException {

    public ChannelTypeNotFoundException() {
        super(NotificationErrorCode.CHANNEL_TYPE_NOT_FOUND);
    }
}
