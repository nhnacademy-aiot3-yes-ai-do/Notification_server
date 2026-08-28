package site.yesaido.notification_server.config.property;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification.access")
public record SubscriptionAccessProperties(
        String cultivationUrl,
        String userUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public SubscriptionAccessProperties {
        if (connectTimeout == null) {
            connectTimeout = Duration.ofSeconds(2);
        }
        if (readTimeout == null) {
            readTimeout = Duration.ofSeconds(3);
        }
    }
}
