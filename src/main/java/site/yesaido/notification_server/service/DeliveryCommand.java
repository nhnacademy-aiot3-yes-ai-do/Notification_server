package site.yesaido.notification_server.service;

public record DeliveryCommand(
        Long deliveryId,
        String channelCode,
        String destination,
        String message,
        short attemptCount
) {

    public short remainingAttempts(short maxAttemptCount) {
        return (short) (maxAttemptCount - attemptCount);
    }
}
