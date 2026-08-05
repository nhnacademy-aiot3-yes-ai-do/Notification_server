package site.yesaido.notification_server.config;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "notification")
public record NotificationProperties(
        Rabbit rabbit,
        Provider provider,
        Retry retry
) {

    public record Rabbit(
            String exchange,
            EventRoute threshold,
            EventRoute action,
            EventRoute daily,
            EventRoute login,
            EventRoute question,
            EventRoute answer,
            EventRoute harvest,
            EventRoute cultivationFinished,
            String deadLetterExchange,
            String deadLetterQueue,
            String deadLetterRoutingKey
    ) {
        public List<EventRoute> eventRoutes() {
            return List.of(
                    threshold,
                    action,
                    daily,
                    login,
                    question,
                    answer,
                    harvest,
                    cultivationFinished);
        }
    }

    public record EventRoute(
            String queue,
            String routingKey
    ) {
    }

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
