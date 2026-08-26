package site.yesaido.notification_server.config.property;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        Provider provider,
        Retry retry
) {
    public record Provider(
            Telegram telegram,
            Discord discord
    ) {
    }

    public record Telegram(
            String baseUrl,
            String botToken
    ) {
    }

    public record Discord(
            List<String> allowedHosts
    ) {
    }

    public record Retry(
            Duration backoff
    ) {
    }
}
