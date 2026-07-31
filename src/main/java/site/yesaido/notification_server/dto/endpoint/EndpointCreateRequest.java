package site.yesaido.notification_server.dto.endpoint;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record EndpointCreateRequest(
        @NotNull @Positive Long channelTypeId,
        @NotBlank @Size(max = 500) String destination,
        @NotBlank @Size(max = 100) String displayName
) {
}
