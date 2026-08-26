package site.yesaido.notification_server.config.property;

import java.time.Duration;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "notification.telegram-link")
public record TelegramLinkProperties(
        @NotBlank @Pattern(regexp = "[A-Za-z0-9_]+") String botUsername,
        @NotBlank @Size(max = 256) @Pattern(regexp = "[A-Za-z0-9_-]+") String webhookSecret,
        @NotNull Duration expiration
) {
}
