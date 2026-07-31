package site.yesaido.notification_server.provider;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.exception.NotificationProviderException;

@Component
public class TelegramSender implements NotificationSender {

    private static final Pattern CHAT_ID = Pattern.compile("-?\\d+");

    private final RestClient restClient;
    private final String botToken;

    public TelegramSender(RestClient.Builder builder, NotificationProperties properties) {
        this.restClient = builder.baseUrl(properties.provider().telegram().baseUrl()).build();
        this.botToken = properties.provider().telegram().botToken();
    }

    @Override
    public String channelCode() {
        return "TELEGRAM";
    }

    @Override
    public void validateDestination(String destination) {
        if (destination == null || !CHAT_ID.matcher(destination).matches()) {
            throw new IllegalArgumentException("Telegram Chat ID 형식이 올바르지 않습니다.");
        }
    }

    @Override
    public ProviderSendResult send(String destination, String message) {
        validateDestination(destination);
        if (botToken == null || botToken.isBlank()) {
            throw new NotificationProviderException("Telegram Bot Token이 설정되지 않았습니다.");
        }

        try {
            JsonNode response = restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .body(Map.of("chat_id", destination, "text", message))
                    .retrieve()
                    .body(JsonNode.class);
            String messageId = response == null
                    ? null
                    : response.path("result").path("message_id").asText(null);
            return new ProviderSendResult(messageId);
        } catch (NotificationProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NotificationProviderException("Telegram 메시지 발송에 실패했습니다.", exception);
        }
    }
}
