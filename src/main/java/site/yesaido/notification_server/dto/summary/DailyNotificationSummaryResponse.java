package site.yesaido.notification_server.dto.summary;

import java.time.LocalDate;
import java.util.List;

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
