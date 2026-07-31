package site.yesaido.notification_server.service;

public record DeliveryCommand(
        Long deliveryId,
        String channelCode,
        String destination,
        String message
) {
}
