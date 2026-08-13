package site.yesaido.notification_server.dto.endpoint;

import java.time.LocalDateTime;
import site.yesaido.notification_server.entity.NotificationEndpoint;

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
                maskDestination(endpoint.getChannelType().getCode(), endpoint.getDestination()),
                endpoint.getDisplayName(),
                endpoint.isEnabled(),
                endpoint.getCreatedAt(),
                endpoint.getUpdatedAt());
    }

    private static String maskDestination(String channelCode, String destination) {
        if (destination == null || destination.isBlank()) {
            return "***";
        }
        if ("TELEGRAM".equals(channelCode)) {
            int exposedLength = Math.min(4, destination.length());
            return "*".repeat(Math.max(3, destination.length() - exposedLength))
                    + destination.substring(destination.length() - exposedLength);
        }
        if ("DISCORD".equals(channelCode)) {
            int lastSlash = destination.lastIndexOf('/');
            return lastSlash < 0 ? "***" : destination.substring(0, lastSlash + 1) + "***";
        }
        return "***";
    }
}
