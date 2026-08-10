package site.yesaido.notification_server.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.exception.NotificationProviderException;

class DiscordSenderHttpTest {

    private static final String WEBHOOK_URL = "https://discord.com/api/webhooks/123456/test-token";

    @Test
    void returnsProviderMessageIdWhenDiscordRespondsSuccessfully() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK_URL + "?wait=true"))
                .andExpect(content().json("{\"content\":\"테스트 메시지\"}"))
                .andRespond(withSuccess("{\"id\":\"discord-message-42\"}",
                        MediaType.APPLICATION_JSON));
        DiscordSender sender = new DiscordSender(builder, properties());

        ProviderSendResult result = sender.send(WEBHOOK_URL, "테스트 메시지");

        assertThat(result.messageId()).isEqualTo("discord-message-42");
        server.verify();
    }

    @Test
    void wrapsDiscordHttpFailureWithoutExposingWebhook() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo(WEBHOOK_URL + "?wait=true"))
                .andRespond(withServerError());
        DiscordSender sender = new DiscordSender(builder, properties());

        assertThatThrownBy(() -> sender.send(WEBHOOK_URL, "테스트 메시지"))
                .isInstanceOf(NotificationProviderException.class)
                .hasMessage("Discord 메시지 발송에 실패했습니다.")
                .hasMessageNotContaining("test-token");
        server.verify();
    }

    private NotificationProperties properties() {
        return new NotificationProperties(
                new NotificationProperties.Provider(
                        new NotificationProperties.Telegram("https://telegram.test", "test-token"),
                        new NotificationProperties.Discord(List.of("discord.com"))),
                new NotificationProperties.Retry(Duration.ZERO));
    }
}
