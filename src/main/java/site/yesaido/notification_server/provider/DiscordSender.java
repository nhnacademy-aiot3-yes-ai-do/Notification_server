package site.yesaido.notification_server.provider;

import java.net.URI;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.exception.NotificationProviderException;

@Component
public class DiscordSender implements NotificationSender {

    private final RestClient restClient;
    private final Set<String> allowedHosts;

    public DiscordSender(RestClient.Builder builder, NotificationProperties properties) {
        this.restClient = builder.build();
        this.allowedHosts = new HashSet<>();
        properties.provider().discord().allowedHosts().stream()
                .map(host -> host.toLowerCase(Locale.ROOT))
                .forEach(allowedHosts::add);
    }

    @Override
    public String channelCode() {
        return "DISCORD";
    }

    @Override
    public void validateDestination(String destination) {
        URI uri;
        try {
            uri = URI.create(destination);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Discord Webhook URL 형식이 올바르지 않습니다.");
        }

        String host = uri.getHost();
        boolean valid = "https".equalsIgnoreCase(uri.getScheme())
                && host != null
                && allowedHosts.contains(host.toLowerCase(Locale.ROOT))
                && uri.getUserInfo() == null
                && uri.getPath().startsWith("/api/webhooks/");
        if (!valid) {
            throw new IllegalArgumentException("허용되지 않은 Discord Webhook URL입니다.");
        }
    }

    @Override
    public ProviderSendResult send(String destination, String message) {
        validateDestination(destination);
        URI webhook = URI.create(destination + (destination.contains("?") ? "&wait=true" : "?wait=true"));
        try {
            Map<?, ?> response = restClient.post()
                    .uri(webhook)
                    .body(Map.of("content", message))
                    .retrieve()
                    .body(Map.class);
            Object messageId = response == null ? null : response.get("id");
            return new ProviderSendResult(messageId == null ? null : String.valueOf(messageId));
        } catch (RuntimeException exception) {
            throw new NotificationProviderException("Discord 메시지 발송에 실패했습니다.", exception);
        }
    }
}
