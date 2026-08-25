package site.yesaido.notification_server.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import site.yesaido.notification_server.dto.summary.DailyNotificationEventCountResponse;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummariesResponse;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummaryResponse;
import site.yesaido.notification_server.dto.summary.PeriodNotificationSummariesResponse;
import site.yesaido.notification_server.dto.summary.PeriodNotificationSummaryResponse;
import site.yesaido.notification_server.exception.GlobalExceptionHandler;
import site.yesaido.notification_server.service.NotificationDailySummaryService;

@ExtendWith(MockitoExtension.class)
class NotificationInternalControllerTest {

    @Mock
    private NotificationDailySummaryService dailySummaryService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(new NotificationInternalController(dailySummaryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void 일일집계를_요청한_재배지_목록으로_반환한다() throws Exception {
        LocalDate date = LocalDate.of(2026, 8, 24);
        when(dailySummaryService.summarizeDaily(date, List.of(11L, 12L)))
                .thenReturn(new DailyNotificationSummariesResponse(
                        date,
                        "Asia/Seoul",
                        List.of(
                                new DailyNotificationSummaryResponse(
                                        11L,
                                        date,
                                        5L,
                                        List.of(
                                                new DailyNotificationEventCountResponse(
                                                        "ENVIRONMENT_THRESHOLD_BREACHED", "환경 이상", 3L),
                                                new DailyNotificationEventCountResponse(
                                                        "ACTUATOR_CONTROL_SUCCEEDED", "제어 성공", 2L))),
                                new DailyNotificationSummaryResponse(12L, date, 0L, List.of()))));

        mockMvc.perform(post("/api/v1/internal/notifications/daily-summaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-08-24","cultivationIds":[11,12]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.date").value("2026-08-24"))
                .andExpect(jsonPath("$.zoneId").value("Asia/Seoul"))
                .andExpect(jsonPath("$.summaries[0].cultivationId").value(11))
                .andExpect(jsonPath("$.summaries[0].totalCount").value(5))
                .andExpect(jsonPath("$.summaries[0].eventCounts[0].eventTypeCode")
                        .value("ENVIRONMENT_THRESHOLD_BREACHED"))
                .andExpect(jsonPath("$.summaries[1].cultivationId").value(12))
                .andExpect(jsonPath("$.summaries[1].totalCount").value(0));

        verify(dailySummaryService).summarizeDaily(date, List.of(11L, 12L));
    }

    @Test
    void 기간집계는_시작일과_종료일_합계를_반환한다() throws Exception {
        LocalDate startDate = LocalDate.of(2026, 8, 18);
        LocalDate endDate = LocalDate.of(2026, 8, 24);
        when(dailySummaryService.summarizePeriod(startDate, endDate, List.of(11L)))
                .thenReturn(new PeriodNotificationSummariesResponse(
                        startDate,
                        endDate,
                        "Asia/Seoul",
                        List.of(new PeriodNotificationSummaryResponse(
                                11L, 5L, List.of()))));

        mockMvc.perform(post("/api/v1/internal/notifications/period-summaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"startDate":"2026-08-18","endDate":"2026-08-24","cultivationIds":[11]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startDate").value("2026-08-18"))
                .andExpect(jsonPath("$.endDate").value("2026-08-24"))
                .andExpect(jsonPath("$.summaries[0].cultivationId").value(11));

        verify(dailySummaryService).summarizePeriod(startDate, endDate, List.of(11L));
    }

    @Test
    void 재배지목록이_비어있으면_거부한다() throws Exception {
        mockMvc.perform(post("/api/v1/internal/notifications/daily-summaries")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"date":"2026-08-24","cultivationIds":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("재배지 ID 목록은 비어 있을 수 없습니다."));
    }
}
