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
import site.yesaido.notification_server.dto.summary.DailyNotificationSummaryResponse;
import site.yesaido.notification_server.repository.NotificationRepository;
import site.yesaido.notification_server.repository.projection.NotificationEventCountProjection;

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
