package site.yesaido.notification_server.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.dto.summary.DailyNotificationEventCountResponse;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummariesResponse;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummaryResponse;
import site.yesaido.notification_server.dto.summary.PeriodNotificationSummariesResponse;
import site.yesaido.notification_server.dto.summary.PeriodNotificationSummaryResponse;
import site.yesaido.notification_server.repository.NotificationRepository;
import site.yesaido.notification_server.validation.ValidationMessages;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationDailySummaryService {

    private static final ZoneId BUSINESS_ZONE = ZoneId.of("Asia/Seoul");

    private final NotificationRepository notificationRepository;

    public DailyNotificationSummaryResponse summarize(Long cultivationId, LocalDate date) {
        OffsetDateTime startAt = date.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime endAt = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        return summarize(cultivationId, date, startAt, endAt);
    }

    public DailyNotificationSummariesResponse summarizeDaily(LocalDate date, List<Long> cultivationIds) {
        OffsetDateTime startAt = date.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime endAt = date.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        Map<Long, List<DailyNotificationEventCountResponse>> eventCountsByCultivation = findEventCounts(
                cultivationIds, startAt, endAt);
        List<DailyNotificationSummaryResponse> summaries = cultivationIds.stream()
                .distinct()
                .map(cultivationId -> dailySummary(cultivationId, date,
                        eventCountsByCultivation.getOrDefault(cultivationId, List.of())))
                .toList();
        return new DailyNotificationSummariesResponse(date, BUSINESS_ZONE.getId(), summaries);
    }

    public PeriodNotificationSummariesResponse summarizePeriod(
            LocalDate startDate, LocalDate endDate, List<Long> cultivationIds) {
        if (startDate.isAfter(endDate)) {
            throw new IllegalArgumentException(ValidationMessages.SUMMARY_DATE_RANGE_INVALID);
        }
        OffsetDateTime startAt = startDate.atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        OffsetDateTime endAt = endDate.plusDays(1).atStartOfDay(BUSINESS_ZONE).toOffsetDateTime();
        Map<Long, List<DailyNotificationEventCountResponse>> eventCountsByCultivation = findEventCounts(
                cultivationIds, startAt, endAt);
        List<PeriodNotificationSummaryResponse> summaries = cultivationIds.stream()
                .distinct()
                .map(cultivationId -> new PeriodNotificationSummaryResponse(
                        cultivationId,
                        totalCount(eventCountsByCultivation.getOrDefault(cultivationId, List.of())),
                        eventCountsByCultivation.getOrDefault(cultivationId, List.of())))
                .toList();
        return new PeriodNotificationSummariesResponse(
                startDate, endDate, BUSINESS_ZONE.getId(), summaries);
    }

    private DailyNotificationSummaryResponse summarize(
            Long cultivationId, LocalDate date, OffsetDateTime startAt, OffsetDateTime endAt) {
        List<DailyNotificationEventCountResponse> eventCounts = findEventCounts(cultivationId, startAt, endAt);
        long totalCount = totalCount(eventCounts);

        return new DailyNotificationSummaryResponse(cultivationId, date, totalCount, eventCounts);
    }

    private DailyNotificationSummaryResponse dailySummary(
            Long cultivationId, LocalDate date, List<DailyNotificationEventCountResponse> eventCounts) {
        return new DailyNotificationSummaryResponse(cultivationId, date, totalCount(eventCounts), eventCounts);
    }

    private List<DailyNotificationEventCountResponse> findEventCounts(
            Long cultivationId, OffsetDateTime startAt, OffsetDateTime endAt) {
        return notificationRepository
                .countEventsByCultivationAndOccurredAtBetween(cultivationId, startAt, endAt)
                .stream()
                .map(projection -> new DailyNotificationEventCountResponse(
                        projection.getEventTypeCode(),
                        projection.getEventTypeName(),
                        projection.getEventCount()))
                .toList();
    }

    private Map<Long, List<DailyNotificationEventCountResponse>> findEventCounts(
            List<Long> cultivationIds, OffsetDateTime startAt, OffsetDateTime endAt) {
        return notificationRepository
                .countEventsByCultivationsAndOccurredAtBetween(
                        cultivationIds.stream().distinct().toList(), startAt, endAt)
                .stream()
                .map(projection -> Map.entry(
                        projection.getCultivationId(),
                        new DailyNotificationEventCountResponse(
                                projection.getEventTypeCode(),
                                projection.getEventTypeName(),
                                projection.getEventCount())))
                .collect(Collectors.groupingBy(
                        Map.Entry::getKey,
                        Collectors.mapping(Map.Entry::getValue, Collectors.toList())));
    }

    private long totalCount(List<DailyNotificationEventCountResponse> eventCounts) {
        return eventCounts.stream()
                .mapToLong(DailyNotificationEventCountResponse::count)
                .sum();
    }
}
