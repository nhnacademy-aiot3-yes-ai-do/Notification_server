package site.yesaido.notification_server.controller;

import jakarta.validation.constraints.Positive;
import java.net.URI;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.telegram.TelegramLinkSessionResponse;
import site.yesaido.notification_server.dto.telegram.TelegramLinkStatusResponse;
import site.yesaido.notification_server.service.TelegramLinkService;
import site.yesaido.notification_server.validation.ValidationMessages;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/telegram-link-sessions")
public class TelegramLinkController {

    private final TelegramLinkService telegramLinkService;

    @GetMapping("/{session-id}")
    public ResponseEntity<TelegramLinkStatusResponse> status(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId,
            @PathVariable("session-id") UUID sessionId
    ) {
        return ResponseEntity.ok(telegramLinkService.status(userId, sessionId));
    }

    @PostMapping
    public ResponseEntity<TelegramLinkSessionResponse> create(
            @RequestHeader("X-User-Id")
            @Positive(message = ValidationMessages.USER_ID_POSITIVE) Long userId
    ) {
        TelegramLinkSessionResponse response = telegramLinkService.create(userId);
        return ResponseEntity.created(URI.create("/api/v1/telegram-link-sessions/" + response.sessionId()))
                .body(response);
    }
}
