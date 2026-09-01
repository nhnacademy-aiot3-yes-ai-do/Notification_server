package site.yesaido.notification_server.dto.admin;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record NotificationTemplateRequest(
        @NotNull
        Long eventTypeId,
        @NotNull
        Long channelTypeId,
        @NotBlank @Size(max = 10000)
        String bodyTemplate,
        Integer version) {
}
