package site.yesaido.notification_server.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.dto.summary.DailyNotificationEventCountResponse;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummaryResponse;
import site.yesaido.notification_server.repository.NotificationRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationDailySummaryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;

    public DailyNotificationSummaryResponse summarize(Long cultivationId, LocalDate date) {
        OffsetDateTime startAt = date.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime endAt = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        List<DailyNotificationEventCountResponse> eventCounts = notificationRepository
                .countEventsByCultivationAndOccurredAtBetween(cultivationId, startAt, endAt)
                .stream()
                .map(projection -> new DailyNotificationEventCountResponse(
                        projection.getEventTypeCode(),
                        projection.getEventTypeName(),
                        projection.getEventCount()))
                .toList();
        long totalCount = eventCounts.stream()
                .mapToLong(DailyNotificationEventCountResponse::count)
                .sum();

        return new DailyNotificationSummaryResponse(cultivationId, date, totalCount, eventCounts);
    }
}
