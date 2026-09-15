package site.yesaido.notification_server.controller;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import site.yesaido.notification_server.controller.docs.TelegramLinkControllerDocs;
import site.yesaido.notification_server.dto.telegram.TelegramLinkSessionResponse;
import site.yesaido.notification_server.dto.telegram.TelegramLinkStatusResponse;
import site.yesaido.notification_server.service.TelegramLinkService;

import java.net.URI;
import java.util.UUID;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/telegram-link-sessions")
public class TelegramLinkController implements TelegramLinkControllerDocs {

    private final TelegramLinkService telegramLinkService;

    @Override    @GetMapping("/{session-id}")
    public ResponseEntity<TelegramLinkStatusResponse> status(
            @RequestHeader("X-User-Id")
            Long userId,
            @PathVariable("session-id") UUID sessionId
    ) {
        return ResponseEntity.ok(telegramLinkService.status(userId, sessionId));
    }

    @Override    @PostMapping
    public ResponseEntity<TelegramLinkSessionResponse> create(
            @RequestHeader("X-User-Id")
            Long userId
    ) {
        TelegramLinkSessionResponse response = telegramLinkService.create(userId);
        return ResponseEntity.created(URI.create("/api/v1/telegram-link-sessions/" + response.sessionId()))
                .body(response);
    }
}
