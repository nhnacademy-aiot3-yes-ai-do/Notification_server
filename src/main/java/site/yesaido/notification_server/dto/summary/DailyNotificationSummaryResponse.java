package site.yesaido.notification_server.dto.summary;

import java.time.LocalDate;
import java.util.List;

/** AI 일일 피드백 계약 확정 전 사용하는 내부 집계 응답 초안이다. */
public record DailyNotificationSummaryResponse(
        Long cultivationId,
        LocalDate date,
        long totalCount,
        List<DailyNotificationEventCountResponse> eventCounts
) {
    public DailyNotificationSummaryResponse {
        eventCounts = List.copyOf(eventCounts);
    }
}
