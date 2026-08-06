package site.yesaido.notification_server.controller;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;
import site.yesaido.notification_server.service.NotificationSubscriptionService;

@ExtendWith(MockitoExtension.class)
class NotificationSubscriptionTypeControllerTest {

    @Mock
    private NotificationSubscriptionService subscriptionService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(
                        new NotificationSubscriptionTypeController(subscriptionService))
                .build();
    }

    @Test
    void returnsAvailableSubscriptionTypes() throws Exception {
        when(subscriptionService.findTypes()).thenReturn(List.of(
                new SubscriptionTypeResponse(
                        5L, "환경 이상", "환경 임계값 초과", "ENVIRONMENT_THRESHOLD_BREACHED",
                        "CULTIVATION")));

        mockMvc.perform(get("/api/v1/notification-subscription-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].eventType").value("ENVIRONMENT_THRESHOLD_BREACHED"));
    }
}
