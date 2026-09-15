package site.yesaido.notification_server.config.property;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "notification.access")
public record SubscriptionAccessProperties(
        String cultivationUrl,
        String userUrl,
        Duration connectTimeout,
        Duration readTimeout
) {
    public SubscriptionAccessProperties {
        connectTimeout = orDefault(connectTimeout, Duration.ofSeconds(2));
        readTimeout = orDefault(readTimeout, Duration.ofSeconds(3));
    }

    private static Duration orDefault(Duration duration, Duration fallback) {
        if (duration == null || duration.isNegative() || duration.isZero()) {
            return fallback;
        }
        return duration;
    }
}
