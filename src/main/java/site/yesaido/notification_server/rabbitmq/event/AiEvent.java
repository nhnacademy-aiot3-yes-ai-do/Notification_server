package site.yesaido.notification_server.rabbitmq.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

public class AiEvent {
    // 일일 피드백 생성 알림
    public record DailyFeedbackGeneratedEvent (
            UUID eventId,
            long userId,
            long cultivationId,
            String cultivationName,
            String feedbackUrl,
            String feedbackContent,
            OffsetDateTime occurredAt
    ) {}

    // 재배 완료 알림
    public record CultivationCompletedEvent (
            UUID eventId,
            long userId,
            long cultivationId,
            String cultivationName,
            BigDecimal growthRate,
            String cultivationUrl,
            OffsetDateTime occurredAt
    ) {}
}
