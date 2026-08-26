package site.yesaido.notification_server.config;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import site.yesaido.notification_server.config.property.TelegramLinkProperties;
import site.yesaido.notification_server.controller.TelegramWebhookController;
import site.yesaido.notification_server.interceptor.TelegramWebhookAuthenticationInterceptor;
import site.yesaido.notification_server.service.TelegramWebhookService;

@WebMvcTest(TelegramWebhookController.class)
@Import({TelegramWebhookWebMvcConfiguration.class, TelegramWebhookAuthenticationInterceptor.class})
@EnableConfigurationProperties(TelegramLinkProperties.class)
@TestPropertySource(properties = {
        "notification.telegram-link.bot-username=yes_ai_do_farm_alert_bot",
        "notification.telegram-link.webhook-secret=test-webhook-secret",
        "notification.telegram-link.expiration=PT10M"
})
class TelegramWebhookWebMvcConfigurationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private TelegramWebhookService telegramWebhookService;

    @Test
    void registersSecretInterceptorForActualWebhookRoute() throws Exception {
        mockMvc.perform(post(TelegramWebhookController.WEBHOOK_PATH)
                        .contentType("application/json")
                        .content("{\"update_id\":1}"))
                .andExpect(status().isForbidden());

        verify(telegramWebhookService, never()).handle(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void allowsActualWebhookRouteOnlyWithMatchingSecret() throws Exception {
        mockMvc.perform(post(TelegramWebhookController.WEBHOOK_PATH)
                        .header("X-Telegram-Bot-Api-Secret-Token", "test-webhook-secret")
                        .contentType("application/json")
                        .content("{\"update_id\":1}"))
                .andExpect(status().isOk());

        verify(telegramWebhookService).handle(org.mockito.ArgumentMatchers.any());
    }
}
