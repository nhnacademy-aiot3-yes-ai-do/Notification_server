package site.yesaido.notification_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.telegram.TelegramWebhookUpdate;
import site.yesaido.notification_server.service.TelegramWebhookService;

@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {

    public static final String WEBHOOK_PATH = "/webhooks/telegram";

    private final TelegramWebhookService telegramWebhookService;

    @PostMapping(WEBHOOK_PATH)
    public ResponseEntity<Void> receive(@RequestBody TelegramWebhookUpdate update) {
        telegramWebhookService.handle(update);
        return ResponseEntity.ok().build();
    }
}
