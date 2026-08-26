package site.yesaido.notification_server.dto.summary;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import site.yesaido.notification_server.validation.ValidationMessages;

public record PeriodNotificationSummaryRequest(
        @NotNull(message = ValidationMessages.SUMMARY_START_DATE_REQUIRED)
        LocalDate startDate,

        @NotNull(message = ValidationMessages.SUMMARY_END_DATE_REQUIRED)
        LocalDate endDate,

        @NotEmpty(message = ValidationMessages.CULTIVATION_IDS_NOT_EMPTY)
        List<@NotNull(message = ValidationMessages.CULTIVATION_ID_POSITIVE)
                @Positive(message = ValidationMessages.CULTIVATION_ID_POSITIVE) Long> cultivationIds
) {
    public PeriodNotificationSummaryRequest {
        cultivationIds = cultivationIds == null ? List.of() : List.copyOf(cultivationIds);
    }
}
