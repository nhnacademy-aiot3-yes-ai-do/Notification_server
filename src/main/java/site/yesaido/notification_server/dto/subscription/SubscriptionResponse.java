package site.yesaido.notification_server.dto.subscription;

import java.time.LocalDateTime;
import site.yesaido.notification_server.domain.NotificationSubscription;

public record SubscriptionResponse(
        Long id,
        Long subscriptionTypeId,
        String subscriptionName,
        String eventType,
        String targetType,
        Long targetId,
        Long endpointId,
        String channelCode,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static SubscriptionResponse from(NotificationSubscription subscription) {
        return new SubscriptionResponse(
                subscription.getId(),
                subscription.getSubscriptionType().getId(),
                subscription.getSubscriptionType().getName(),
                subscription.getSubscriptionType().getEventType().getCode(),
                subscription.getSubscriptionType().getTargetType().getTargetType(),
                subscription.getTargetId(),
                subscription.getEndpoint().getId(),
                subscription.getEndpoint().getChannelType().getCode(),
                subscription.isEnabled(),
                subscription.getCreatedAt(),
                subscription.getUpdatedAt());
    }
}
