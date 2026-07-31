package site.yesaido.notification_server.provider;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.exception.NotificationProviderException;

class NotificationSenderTest {

    @Test
    void telegramValidatesChatIdAndRequiresToken() {
        TelegramSender sender = new TelegramSender(RestClient.builder(), properties(""));

        sender.validateDestination("-123456789");
        assertThatThrownBy(() -> sender.validateDestination("not-a-chat-id"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> sender.send("123456789", "message"))
                .isInstanceOf(NotificationProviderException.class)
                .hasMessageContaining("Token");
    }

    @Test
    void discordAllowsOnlyOfficialWebhookHosts() {
        DiscordSender sender = new DiscordSender(RestClient.builder(), properties(""));

        sender.validateDestination("https://discord.com/api/webhooks/123/token");
        assertThatThrownBy(() ->
                sender.validateDestination("https://example.com/api/webhooks/123/token"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() ->
                sender.validateDestination("http://discord.com/api/webhooks/123/token"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    private NotificationProperties properties(String telegramToken) {
        return new NotificationProperties(
                new NotificationProperties.Rabbit(
                        "events", "queue", "#", "dlx", "dlq", "failed"),
                new NotificationProperties.Provider(
                        new NotificationProperties.Telegram(
                                "https://api.telegram.org", telegramToken),
                        new NotificationProperties.Discord(
                                List.of("discord.com", "discordapp.com"))),
                new NotificationProperties.Retry(3, Duration.ZERO));
    }
}
