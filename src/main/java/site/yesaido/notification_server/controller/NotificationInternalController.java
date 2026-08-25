package site.yesaido.notification_server.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummariesResponse;
import site.yesaido.notification_server.dto.summary.DailyNotificationSummaryRequest;
import site.yesaido.notification_server.dto.summary.PeriodNotificationSummariesResponse;
import site.yesaido.notification_server.dto.summary.PeriodNotificationSummaryRequest;
import site.yesaido.notification_server.service.NotificationDailySummaryService;

@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1/internal/notifications")
public class NotificationInternalController {

    private final NotificationDailySummaryService dailySummaryService;

    @PostMapping("/daily-summaries")
    public ResponseEntity<DailyNotificationSummariesResponse> findDailySummaries(
            @Valid @RequestBody DailyNotificationSummaryRequest request
    ) {
        return ResponseEntity.ok(dailySummaryService.summarizeDaily(request.date(), request.cultivationIds()));
    }

    @PostMapping("/period-summaries")
    public ResponseEntity<PeriodNotificationSummariesResponse> findPeriodSummaries(
            @Valid @RequestBody PeriodNotificationSummaryRequest request
    ) {
        return ResponseEntity.ok(dailySummaryService.summarizePeriod(
                request.startDate(), request.endDate(), request.cultivationIds()));
    }
}
