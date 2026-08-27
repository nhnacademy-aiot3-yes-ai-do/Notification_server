package site.yesaido.notification_server.service;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.notification_server.dto.telegram.TelegramWebhookUpdate;

@ExtendWith(MockitoExtension.class)
class TelegramWebhookServiceTest {

    @Mock
    private TelegramLinkService telegramLinkService;

    @InjectMocks
    private TelegramWebhookService telegramWebhookService;

    @Test
    void completesLinkForPrivateChatStartCommand() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque-token",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private")));

        telegramWebhookService.handle(update);

        verify(telegramLinkService).completeStart("opaque-token", "123456");
    }

    @Test
    void ignoresStartCommandFromNonPrivateChat() {
        TelegramWebhookUpdate update = new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque-token",
                        new TelegramWebhookUpdate.TelegramChat(-100L, "group")));

        telegramWebhookService.handle(update);

        verify(telegramLinkService, never()).completeStart("opaque-token", "-100");
    }

    @Test
    void ignoresMalformedOrNonStartUpdates() {
        telegramWebhookService.handle(null);
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L, null));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque-token", null)));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque-token",
                        new TelegramWebhookUpdate.TelegramChat(null, "private"))));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("not-a-start",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private"))));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start ",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private"))));
        telegramWebhookService.handle(new TelegramWebhookUpdate(1L,
                new TelegramWebhookUpdate.TelegramMessage("/start opaque token",
                        new TelegramWebhookUpdate.TelegramChat(123456L, "private"))));

        verify(telegramLinkService, never()).completeStart(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }
}
