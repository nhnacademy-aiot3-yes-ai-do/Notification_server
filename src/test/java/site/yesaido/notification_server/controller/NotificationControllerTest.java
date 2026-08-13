package site.yesaido.notification_server.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.yesaido.notification_server.dto.delivery.DeliveryPageResponse;
import site.yesaido.notification_server.dto.delivery.DeliveryResponse;
import site.yesaido.notification_server.entity.DeliveryStatus;
import site.yesaido.notification_server.exception.GlobalExceptionHandler;
import site.yesaido.notification_server.service.NotificationQueryService;

@ExtendWith(MockitoExtension.class)
class NotificationControllerTest {

    @Mock
    private NotificationQueryService queryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PageableHandlerMethodArgumentResolver pageableResolver = new PageableHandlerMethodArgumentResolver();
        pageableResolver.setMaxPageSize(100);
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(queryService))
                .setCustomArgumentResolvers(pageableResolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsNotificationHistoryForUser() throws Exception {
        when(queryService.findDeliveries(eq(7L), any(Pageable.class))).thenReturn(new DeliveryPageResponse(
                List.of(new DeliveryResponse(11L, 101L, 31L, "TELEGRAM",
                        "펌프가 정상적으로 작동했습니다.", DeliveryStatus.SENT, (short) 1, null, null)),
                0, 20, 1, 1, false));

        mockMvc.perform(get("/api/v1/notifications").header("X-User-Id", "7"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].id").value(11))
                .andExpect(jsonPath("$.content[0].status").value("SENT"))
                .andExpect(jsonPath("$.page").value(0));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).findDeliveries(eq(7L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageNumber()).isZero();
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(20);
    }

    @Test
    void rejectsMissingUserHeader() throws Exception {
        mockMvc.perform(get("/api/v1/notifications"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("필수 헤더가 누락되었습니다: X-User-Id"));
    }

    @Test
    void capsExcessivePageSizeAtConfiguredMaximum() throws Exception {
        when(queryService.findDeliveries(eq(7L), any(Pageable.class))).thenReturn(new DeliveryPageResponse(
                List.of(), 0, 100, 0, 0, false));

        mockMvc.perform(get("/api/v1/notifications")
                        .header("X-User-Id", "7")
                        .param("size", "101"))
                .andExpect(status().isOk());

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(queryService).findDeliveries(eq(7L), pageableCaptor.capture());
        org.assertj.core.api.Assertions.assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
    }
}
