package site.yesaido.notification_server.provider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.test.web.client.MockRestServiceServer;
import site.yesaido.notification_server.config.property.NotificationProperties;
import site.yesaido.notification_server.exception.provider.TelegramNotificationProviderException;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TelegramSenderHttpTest {

    @Test
    void returnsProviderMessageIdWhenTelegramResponseIsOk() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bottest-token/sendMessage"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{\"message_id\":42}}",
                        MediaType.APPLICATION_JSON));
        TelegramSender sender = new TelegramSender(builder, properties());

        ProviderSendResult result = sender.send("123456", "테스트 메시지");

        assertThat(result.messageId()).isEqualTo("42");
        server.verify();
    }

    @Test
    void throwsWhenTelegramReturnsOkFalseEvenWithHttp200() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bottest-token/sendMessage"))
                .andRespond(withSuccess("{\"ok\":false,\"description\":\"chat not found\"}",
                        MediaType.APPLICATION_JSON));
        TelegramSender sender = new TelegramSender(builder, properties());

        assertThatThrownBy(() -> sender.send("123456", "테스트 메시지"))
                .isInstanceOf(TelegramNotificationProviderException.class)
                .hasMessageContaining("chat not found");
    }

    @Test
    void returnsNullMessageIdWhenTelegramDoesNotProvideMessageId() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("https://telegram.test/bottest-token/sendMessage"))
                .andRespond(withSuccess("{\"ok\":true,\"result\":{}}", MediaType.APPLICATION_JSON));
        TelegramSender sender = new TelegramSender(builder, properties());

        ProviderSendResult result = sender.send("123456", "테스트 메시지");

        assertThat(result.messageId()).isNull();
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
