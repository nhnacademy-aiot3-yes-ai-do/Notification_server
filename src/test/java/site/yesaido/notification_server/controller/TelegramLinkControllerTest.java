package site.yesaido.notification_server.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.yesaido.notification_server.config.property.TelegramLinkProperties;
import site.yesaido.notification_server.dto.telegram.TelegramLinkSessionResponse;
import site.yesaido.notification_server.dto.telegram.TelegramLinkStatusResponse;
import site.yesaido.notification_server.dto.telegram.TelegramWebhookUpdate;
import site.yesaido.notification_server.exception.GlobalExceptionHandler;
import site.yesaido.notification_server.interceptor.TelegramWebhookAuthenticationInterceptor;
import site.yesaido.notification_server.service.TelegramLinkService;
import site.yesaido.notification_server.service.TelegramWebhookService;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@ExtendWith(MockitoExtension.class)
class TelegramLinkControllerTest {

    @Mock
    private TelegramLinkService service;

    @Mock
    private TelegramWebhookService webhookService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TelegramLinkProperties properties = new TelegramLinkProperties(
                "yes_ai_do_farm_alert_bot", "webhook-secret", null);
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new TelegramLinkController(service),
                        new TelegramWebhookController(webhookService))
                .addMappedInterceptors(
                        new String[] {TelegramWebhookAuthenticationInterceptor.WEBHOOK_PATH},
                        new TelegramWebhookAuthenticationInterceptor(properties))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsAuthenticatedUsersTelegramDeepLink() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.create(7L)).thenReturn(new TelegramLinkSessionResponse(
                sessionId,
                "PENDING",
                "https://t.me/yes_ai_do_farm_alert_bot?start=opaque",
                Instant.parse("2026-08-25T02:10:00Z")));

        mockMvc.perform(post("/api/v1/telegram-link-sessions").header("X-User-Id", "7"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/telegram-link-sessions/" + sessionId))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.deepLink").value("https://t.me/yes_ai_do_farm_alert_bot?start=opaque"));
    }

    @Test
    void returnsLinkStatusOnlyToOwningUser() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(service.status(7L, sessionId)).thenReturn(new TelegramLinkStatusResponse(sessionId, "LINKED"));

        mockMvc.perform(get("/api/v1/telegram-link-sessions/{sessionId}", sessionId).header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LINKED"));
    }

    @Test
    void delegatesAuthenticatedWebhookUpdateToWebhookService() throws Exception {
        mockMvc.perform(post("/webhooks/telegram")
                        .header("X-Telegram-Bot-Api-Secret-Token", "webhook-secret")
                        .contentType("application/json")
                        .content("""
                                {"update_id":1,"message":{"text":"/start opaque","chat":{"id":123456}}}
                                """))
                .andExpect(status().isOk());

        ArgumentCaptor<TelegramWebhookUpdate> updateCaptor = ArgumentCaptor.forClass(TelegramWebhookUpdate.class);
        verify(webhookService).handle(updateCaptor.capture());
        assertThat(updateCaptor.getValue().updateId()).isEqualTo(1L);
        assertThat(updateCaptor.getValue().message().text()).isEqualTo("/start opaque");
        assertThat(updateCaptor.getValue().message().chat().id()).isEqualTo(123456L);
    }

    @Test
    void rejectsWebhookRequestWithoutMatchingSecret() throws Exception {
        mockMvc.perform(post("/webhooks/telegram")
                        .contentType("application/json")
                        .content("{\"update_id\":1}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejectsWebhookRequestWithWrongSecret() throws Exception {
        mockMvc.perform(post("/webhooks/telegram")
                        .header("X-Telegram-Bot-Api-Secret-Token", "wrong-secret")
                        .contentType("application/json")
                        .content("{\"update_id\":1}"))
                .andExpect(status().isForbidden());
    }
}
