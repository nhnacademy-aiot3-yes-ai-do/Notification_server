package site.yesaido.notification_server.dto.summary;

import java.time.LocalDate;
import java.util.List;

public record DailyNotificationSummariesResponse(
        LocalDate date,
        String zoneId,
        List<DailyNotificationSummaryResponse> summaries
) {
    public DailyNotificationSummariesResponse {
        summaries = List.copyOf(summaries);
    }
}
