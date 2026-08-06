package site.yesaido.notification_server.dto.subscription;

import jakarta.validation.constraints.NotNull;

public record SubscriptionEnabledRequest(
        @NotNull(message = "활성화 여부는 필수입니다.") Boolean enabled
) {
}
