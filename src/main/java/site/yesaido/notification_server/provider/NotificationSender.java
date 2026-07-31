package site.yesaido.notification_server.provider;

public interface NotificationSender {

    String channelCode();

    void validateDestination(String destination);

    ProviderSendResult send(String destination, String message);
}
