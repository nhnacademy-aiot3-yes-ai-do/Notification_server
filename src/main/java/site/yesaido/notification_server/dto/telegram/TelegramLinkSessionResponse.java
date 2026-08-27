package site.yesaido.notification_server.dto.telegram;

import java.time.Instant;
import java.util.UUID;

public record TelegramLinkSessionResponse(
        UUID sessionId,
        String status,
        String deepLink,
        Instant expiresAt
) {
}
