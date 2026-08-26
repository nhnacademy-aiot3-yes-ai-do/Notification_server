package site.yesaido.notification_server.dto.summary;

import java.util.List;

public record PeriodNotificationSummaryResponse(
        Long cultivationId,
        long totalCount,
        List<DailyNotificationEventCountResponse> eventCounts
) {
    public PeriodNotificationSummaryResponse {
        eventCounts = List.copyOf(eventCounts);
    }
}
