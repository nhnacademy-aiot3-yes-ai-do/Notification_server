package site.yesaido.notification_server.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import site.yesaido.notification_server.interceptor.TelegramWebhookAuthenticationInterceptor;

@Configuration
@RequiredArgsConstructor
public class TelegramWebhookWebMvcConfiguration implements WebMvcConfigurer {

    private final TelegramWebhookAuthenticationInterceptor telegramWebhookAuthenticationInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(telegramWebhookAuthenticationInterceptor)
                .addPathPatterns(TelegramWebhookAuthenticationInterceptor.WEBHOOK_PATH);
    }
}
