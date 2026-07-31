package site.yesaido.notification_server.dto.endpoint;

import jakarta.validation.constraints.NotNull;

public record EndpointEnabledRequest(@NotNull Boolean enabled) {
}
