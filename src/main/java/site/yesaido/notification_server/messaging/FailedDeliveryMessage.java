package site.yesaido.notification_server.messaging;

import java.time.OffsetDateTime;

public record FailedDeliveryMessage(
        Long deliveryId,
        String reason,
        OffsetDateTime failedAt
) {
}
