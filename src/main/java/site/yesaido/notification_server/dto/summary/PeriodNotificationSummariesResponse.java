package site.yesaido.notification_server.dto.summary;

import java.time.LocalDate;
import java.util.List;

public record PeriodNotificationSummariesResponse(
        LocalDate startDate,
        LocalDate endDate,
        String zoneId,
        List<PeriodNotificationSummaryResponse> summaries
) {
    public PeriodNotificationSummariesResponse {
        summaries = List.copyOf(summaries);
    }
}
