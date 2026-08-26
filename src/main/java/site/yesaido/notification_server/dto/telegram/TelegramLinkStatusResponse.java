package site.yesaido.notification_server.dto.telegram;

import java.util.UUID;

public record TelegramLinkStatusResponse(
        UUID sessionId,
        String status
) {
}
