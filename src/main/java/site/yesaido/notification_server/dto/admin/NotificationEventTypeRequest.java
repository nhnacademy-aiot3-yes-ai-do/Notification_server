package site.yesaido.notification_server.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NotificationEventTypeRequest(
        @NotBlank(message = "이벤트 코드는 필수입니다.")
        @Pattern(regexp = "[A-Z][A-Z0-9_]*", message = "이벤트 코드는 영문 대문자, 숫자, 언더바만 사용할 수 있습니다.")
        @Size(max = 50, message = "이벤트 코드는 50자 이하여야 합니다.")
        String code,
        @NotBlank(message = "이벤트 이름은 필수입니다.")
        @Size(max = 150, message = "이벤트 이름은 150자 이하여야 합니다.")
        String displayName,
        @Size(max = 500, message = "이벤트 설명은 500자 이하여야 합니다.")
        String description,
        @NotBlank(message = "대상 타입은 필수입니다.")
        @Pattern(regexp = "USER|CULTIVATION|INQUIRY", message = "대상 타입이 올바르지 않습니다.")
        String targetType
) {
}
