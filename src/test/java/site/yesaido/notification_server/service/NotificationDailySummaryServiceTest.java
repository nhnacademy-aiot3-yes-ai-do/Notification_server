package site.yesaido.notification_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import site.yesaido.notification_server.dto.summary.DailyNotificationEventCountResponse;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummariesResponse;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummaryResponse;
import site.yesaido.notification_server.dto.summary.PeriodNotificationSummariesResponse;
import site.yesaido.notification_server.dto.summary.PeriodNotificationSummaryResponse;
import site.yesaido.notification_server.repository.NotificationRepository;
import site.yesaido.notification_server.repository.projection.NotificationEventCountProjection;
import site.yesaido.notification_server.validation.ValidationMessages;

@ExtendWith(MockitoExtension.class)
class NotificationDailySummaryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    @InjectMocks
    private NotificationDailySummaryService service;

    @Test
    void 한국시간_하루의_이벤트유형별_건수와_전체건수를_반환한다() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        OffsetDateTime startAt = OffsetDateTime.parse("2026-08-20T00:00:00+09:00");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-08-21T00:00:00+09:00");
        when(notificationRepository.countEventsByCultivationAndOccurredAtBetween(4L, startAt, endAt))
                .thenReturn(List.of(
                        new StubEventCount("ENVIRONMENT_RECOVERED", "환경 복구", 2L),
                        new StubEventCount("ENVIRONMENT_THRESHOLD_BREACHED", "환경 이상", 3L)));

        DailyNotificationSummaryResponse response = service.summarize(4L, date);

        assertThat(response.cultivationId()).isEqualTo(4L);
        assertThat(response.date()).isEqualTo(date);
        assertThat(response.totalCount()).isEqualTo(5L);
        assertThat(response.eventCounts()).extracting("eventTypeCode", "count")
                .containsExactly(
                        tuple("ENVIRONMENT_RECOVERED", 2L),
                        tuple("ENVIRONMENT_THRESHOLD_BREACHED", 3L));
        verify(notificationRepository).countEventsByCultivationAndOccurredAtBetween(4L, startAt, endAt);
    }

    @Test
    void 이벤트가_없으면_전체건수_0과_빈목록을_반환한다() {
        LocalDate date = LocalDate.of(2026, 8, 20);
        when(notificationRepository.countEventsByCultivationAndOccurredAtBetween(
                4L, OffsetDateTime.parse("2026-08-20T00:00:00+09:00"),
                OffsetDateTime.parse("2026-08-21T00:00:00+09:00")))
                .thenReturn(List.of());

        DailyNotificationSummaryResponse response = service.summarize(4L, date);

        assertThat(response.totalCount()).isZero();
        assertThat(response.eventCounts()).isEmpty();
    }

    @Test
    void 여러_재배지의_하루_집계를_요청_순서대로_반환한다() {
        LocalDate date = LocalDate.of(2026, 8, 24);
        OffsetDateTime startAt = OffsetDateTime.parse("2026-08-24T00:00:00+09:00");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-08-25T00:00:00+09:00");
        when(notificationRepository.countEventsByCultivationAndOccurredAtBetween(11L, startAt, endAt))
                .thenReturn(List.of(new StubEventCount("ENVIRONMENT_THRESHOLD_BREACHED", "환경 이상", 3L)));
        when(notificationRepository.countEventsByCultivationAndOccurredAtBetween(12L, startAt, endAt))
                .thenReturn(List.of());

        DailyNotificationSummariesResponse response = service.summarizeDaily(date, List.of(11L, 12L));

        assertThat(response.date()).isEqualTo(date);
        assertThat(response.zoneId()).isEqualTo("Asia/Seoul");
        assertThat(response.summaries()).extracting(
                        DailyNotificationSummaryResponse::cultivationId,
                        DailyNotificationSummaryResponse::totalCount)
                .containsExactly(tuple(11L, 3L), tuple(12L, 0L));
        assertThat(response.summaries().get(0).eventCounts())
                .extracting("eventTypeCode", "count")
                .containsExactly(tuple("ENVIRONMENT_THRESHOLD_BREACHED", 3L));
        assertThat(response.summaries().get(1).eventCounts()).isEmpty();
    }

    @Test
    void 기간_합계는_시작일_포함_종료일_다음날_전까지_한건으로_집계한다() {
        LocalDate startDate = LocalDate.of(2026, 8, 18);
        LocalDate endDate = LocalDate.of(2026, 8, 24);
        OffsetDateTime startAt = OffsetDateTime.parse("2026-08-18T00:00:00+09:00");
        OffsetDateTime endAt = OffsetDateTime.parse("2026-08-25T00:00:00+09:00");
        when(notificationRepository.countEventsByCultivationAndOccurredAtBetween(11L, startAt, endAt))
                .thenReturn(List.of(
                        new StubEventCount("ENVIRONMENT_THRESHOLD_BREACHED", "환경 이상", 3L),
                        new StubEventCount("ACTUATOR_CONTROL_SUCCEEDED", "제어 성공", 2L)));

        PeriodNotificationSummariesResponse response =
                service.summarizePeriod(startDate, endDate, List.of(11L));

        assertThat(response.startDate()).isEqualTo(startDate);
        assertThat(response.endDate()).isEqualTo(endDate);
        assertThat(response.zoneId()).isEqualTo("Asia/Seoul");
        assertThat(response.summaries()).hasSize(1);
        assertThat(response.summaries().getFirst()).isEqualTo(
                new PeriodNotificationSummaryResponse(11L, 5L, List.of(
                        new DailyNotificationEventCountResponse(
                                "ENVIRONMENT_THRESHOLD_BREACHED", "환경 이상", 3L),
                        new DailyNotificationEventCountResponse(
                                "ACTUATOR_CONTROL_SUCCEEDED", "제어 성공", 2L))));
        assertThat(response.summaries().getFirst().totalCount()).isEqualTo(5L);
        verify(notificationRepository).countEventsByCultivationAndOccurredAtBetween(11L, startAt, endAt);
    }

    @Test
    void 시작일이_종료일보다_이후면_거부한다() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() ->
                        service.summarizePeriod(
                                LocalDate.of(2026, 8, 25),
                                LocalDate.of(2026, 8, 24),
                                List.of(11L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(ValidationMessages.SUMMARY_DATE_RANGE_INVALID);
    }

    private record StubEventCount(String eventTypeCode, String eventTypeName, Long eventCount)
            implements NotificationEventCountProjection {
        @Override
        public String getEventTypeCode() {
            return eventTypeCode;
        }

        @Override
        public String getEventTypeName() {
            return eventTypeName;
        }

        @Override
        public Long getEventCount() {
            return eventCount;
        }
    }
}
