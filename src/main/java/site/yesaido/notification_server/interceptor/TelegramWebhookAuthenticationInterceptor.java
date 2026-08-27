package site.yesaido.notification_server.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import site.yesaido.notification_server.config.property.TelegramLinkProperties;
import site.yesaido.notification_server.controller.TelegramWebhookController;

@Component
@RequiredArgsConstructor
public class TelegramWebhookAuthenticationInterceptor implements HandlerInterceptor {

    public static final String WEBHOOK_PATH = TelegramWebhookController.WEBHOOK_PATH;
    private static final String SECRET_HEADER = "X-Telegram-Bot-Api-Secret-Token";

    private final TelegramLinkProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String expected = properties.webhookSecret();
        String actual = request.getHeader(SECRET_HEADER);
        boolean matches = expected != null && !expected.isBlank() && actual != null
                && MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8), actual.getBytes(StandardCharsets.UTF_8));
        if (matches) {
            return true;
        }
        response.setStatus(HttpServletResponse.SC_FORBIDDEN);
        return false;
    }
}
