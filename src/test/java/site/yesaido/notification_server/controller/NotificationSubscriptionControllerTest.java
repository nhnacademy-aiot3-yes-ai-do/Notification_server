package site.yesaido.notification_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionResponse;
import site.yesaido.notification_server.exception.GlobalExceptionHandler;
import site.yesaido.notification_server.service.NotificationSubscriptionService;

@ExtendWith(MockitoExtension.class)
class NotificationSubscriptionControllerTest {

    @Mock
    private NotificationSubscriptionService subscriptionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationSubscriptionController(subscriptionService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createsSubscriptionAndReturnsLocation() throws Exception {
        when(subscriptionService.create(any(Long.class), any(SubscriptionCreateRequest.class)))
                .thenReturn(new SubscriptionResponse(
                        31L, 5L, "환경 이상", "ENVIRONMENT_THRESHOLD_BREACHED", "CULTIVATION",
                        101L, 21L, "TELEGRAM", true, null, null));

        mockMvc.perform(post("/api/v1/notification-subscriptions")
                        .header("X-User-Id", "7")
                        .contentType("application/json")
                        .content("{\"subscriptionTypeId\":5,\"endpointId\":21,\"targetId\":101}"))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/notification-subscriptions/31"))
                .andExpect(jsonPath("$.targetType").value("CULTIVATION"));
    }

    @Test
    void listsSubscriptions() throws Exception {
        when(subscriptionService.findAll(7L)).thenReturn(List.of(
                new SubscriptionResponse(
                        31L, 5L, "환경 이상", "ENVIRONMENT_THRESHOLD_BREACHED", "CULTIVATION",
                        101L, 21L, "TELEGRAM", true, null, null)));

        mockMvc.perform(get("/api/v1/notification-subscriptions").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].targetId").value(101));
    }

    @Test
    void deletesSubscription() throws Exception {
        mockMvc.perform(delete("/api/v1/notification-subscriptions/31")
                        .header("X-User-Id", "7"))
                .andExpect(status().isNoContent());

        verify(subscriptionService).delete(7L, 31L);
    }
}
