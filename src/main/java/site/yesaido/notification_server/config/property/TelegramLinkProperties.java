package site.yesaido.notification_server.config.property;

import jakarta.validation.constraints.*;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "notification.telegram-link")
public record TelegramLinkProperties(
        @NotBlank @Pattern(regexp = "\\w+") String botUsername,
        @NotBlank @Size(max = 256) @Pattern(regexp = "[A-Za-z0-9_-]+") String webhookSecret,
        @NotNull Duration expiration
) {
    @AssertTrue(message = "expiration must be at least one whole second")
    boolean hasRedisCompatibleExpiration() {
        return expiration != null && expiration.toSeconds() > 0;
    }
}
