package site.yesaido.notification_server.dto.subscription;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record SubscriptionCreateRequest(
        @NotNull(message = "알림 구독 유형 ID는 필수입니다.")
        @Positive(message = "알림 구독 유형 ID는 1 이상이어야 합니다.")
        Long subscriptionTypeId,

        @NotNull(message = "알림 수신 경로 ID는 필수입니다.")
        @Positive(message = "알림 수신 경로 ID는 1 이상이어야 합니다.")
        Long endpointId,

        @NotNull(message = "알림 대상 ID는 필수입니다.")
        @Positive(message = "알림 대상 ID는 1 이상이어야 합니다.")
        Long targetId
) {
}
