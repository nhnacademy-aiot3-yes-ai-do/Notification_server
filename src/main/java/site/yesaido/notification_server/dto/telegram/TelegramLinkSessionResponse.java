package site.yesaido.notification_server.dto.telegram;

import java.time.LocalDateTime;
import java.util.UUID;

public record TelegramLinkSessionResponse(
        UUID sessionId,
        String status,
        String deepLink,
        LocalDateTime expiresAt
) {
}
