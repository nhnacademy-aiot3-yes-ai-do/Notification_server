package site.yesaido.notification_server.provider;

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
            Map<?, ?> response = restClient.post()
                    .uri("/bot{token}/sendMessage", botToken)
                    .body(Map.of("chat_id", destination, "text", message))
                    .retrieve()
                    .body(Map.class);
            if (response == null || !Boolean.TRUE.equals(response.get("ok"))) {
                Object descriptionValue = response == null ? null : response.get("description");
                String description = descriptionValue == null
                        ? "알 수 없는 Telegram API 오류입니다."
                        : String.valueOf(descriptionValue);
                throw new NotificationProviderException("Telegram 메시지 발송에 실패했습니다: "
                        + description);
            }
            Object result = response.get("result");
            Object rawMessageId = result instanceof Map<?, ?> resultMap
                    ? resultMap.get("message_id")
                    : null;
            String messageId = rawMessageId == null ? null : String.valueOf(rawMessageId);
            return new ProviderSendResult(messageId);
        } catch (NotificationProviderException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new NotificationProviderException("Telegram 메시지 발송에 실패했습니다.", exception);
        }
    }
}
