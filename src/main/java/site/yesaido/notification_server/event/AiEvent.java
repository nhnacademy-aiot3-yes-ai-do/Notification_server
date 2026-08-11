package site.yesaido.notification_server.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class AiEvent {
    // 일일 피드백 생성 알림
    public record DailyFeedbackGeneratedEvent (
            long userId,
            long cultivationId,
            String cultivationName,
            String feedbackUrl,
            String feedbackContent,
            OffsetDateTime occurredAt
    ) {}

    // 재배 완료 알림
    public record CultivationCompletedEvent (
            long userId,
            long cultivationId,
            String cultivationName,
            BigDecimal growthRate,
            String cultivationUrl,
            OffsetDateTime occurredAt
    ) {}
}
