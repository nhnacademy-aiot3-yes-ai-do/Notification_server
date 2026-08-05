package site.yesaido.notification_server.dto.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EndpointCreateRequest(
        @NotNull(message = "채널 유형 ID는 필수입니다.")
        @Positive(message = "채널 유형 ID는 1 이상이어야 합니다.")
        Long channelTypeId,

        @NotBlank(message = "알림 수신 주소는 필수입니다.")
        @Size(max = 500, message = "알림 수신 주소는 500자 이하여야 합니다.")
        String destination,

        @NotBlank(message = "알림 수신 경로 이름은 필수입니다.")
        @Size(max = 100, message = "알림 수신 경로 이름은 100자 이하여야 합니다.")
        String displayName
) {
}
