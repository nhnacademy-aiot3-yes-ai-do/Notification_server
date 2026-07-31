package site.yesaido.notification_server.dto.subscription;

import jakarta.validation.constraints.NotNull;

public record SubscriptionEnabledRequest(@NotNull Boolean enabled) {
}
