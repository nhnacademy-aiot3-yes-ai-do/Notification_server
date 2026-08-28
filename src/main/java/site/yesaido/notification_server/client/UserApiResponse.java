package site.yesaido.notification_server.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record UserApiResponse<T>(
        Boolean success,
        String message,
        T data
) {
}
