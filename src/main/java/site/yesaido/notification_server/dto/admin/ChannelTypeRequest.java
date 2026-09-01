package site.yesaido.notification_server.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ChannelTypeRequest(
        @NotBlank @Size(max = 30)
        String code,
        @NotBlank @Size(max = 100)
        String displayName) {
}
