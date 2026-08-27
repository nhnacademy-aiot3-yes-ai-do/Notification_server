package site.yesaido.notification_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import site.yesaido.notification_server.dto.telegram.TelegramWebhookUpdate;

@Service
@RequiredArgsConstructor
public class TelegramWebhookService {

    private static final String PRIVATE_CHAT = "private";
    private static final String START_COMMAND_PREFIX = "/start ";

    private final TelegramLinkService telegramLinkService;

    public void handle(TelegramWebhookUpdate update) {
        if (update == null || update.message() == null || update.message().chat() == null) {
            return;
        }

        TelegramWebhookUpdate.TelegramChat chat = update.message().chat();
        String token = startToken(update.message().text());
        if (!PRIVATE_CHAT.equals(chat.type()) || chat.id() == null || token == null) {
            return;
        }

        telegramLinkService.completeStart(token, String.valueOf(chat.id()));
    }

    private String startToken(String text) {
        if (text == null || !text.startsWith(START_COMMAND_PREFIX)) {
            return null;
        }
        String token = text.substring(START_COMMAND_PREFIX.length());
        return token.isBlank() || token.contains(" ") ? null : token;
    }
}
