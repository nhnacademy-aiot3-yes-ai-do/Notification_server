package site.yesaido.notification_server.dto.telegram;

import com.fasterxml.jackson.annotation.JsonProperty;

public record TelegramWebhookUpdate(
        @JsonProperty("update_id") Long updateId,
        TelegramMessage message
) {
    public record TelegramMessage(String text, TelegramChat chat) {
    }

    public record TelegramChat(Long id, String type) {
    }
}
