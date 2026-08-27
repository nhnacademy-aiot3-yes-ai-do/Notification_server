package site.yesaido.notification_server.rabbitmq.event;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record MemberAddedPayload(
        Long cultivationId,
        String cultivationName,
        String role
) {
}
