package site.yesaido.notification_server.dto.endpoint;

import java.time.LocalDateTime;
import site.yesaido.notification_server.domain.NotificationEndpoint;

public record EndpointResponse(
        Long id,
        Long channelTypeId,
        String channelCode,
        String channelName,
        String destination,
        String displayName,
        boolean enabled,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static EndpointResponse from(NotificationEndpoint endpoint) {
        return new EndpointResponse(
                endpoint.getId(),
                endpoint.getChannelType().getId(),
                endpoint.getChannelType().getCode(),
                endpoint.getChannelType().getDisplayName(),
                endpoint.getDestination(),
                endpoint.getDisplayName(),
                endpoint.isEnabled(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt());
    }
}
