package site.yesaido.notification_server.dto.summary;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.time.LocalDate;
import java.util.List;
import site.yesaido.notification_server.validation.ValidationMessages;

public record DailyNotificationSummaryRequest(
        @NotNull(message = ValidationMessages.SUMMARY_DATE_REQUIRED)
        LocalDate date,

        @NotEmpty(message = ValidationMessages.CULTIVATION_IDS_NOT_EMPTY)
        List<@NotNull(message = ValidationMessages.CULTIVATION_ID_POSITIVE)
                @Positive(message = ValidationMessages.CULTIVATION_ID_POSITIVE) Long> cultivationIds
) {
    public DailyNotificationSummaryRequest {
        cultivationIds = cultivationIds == null ? List.of() : List.copyOf(cultivationIds);
    }
}
