package site.yesaido.notification_server.rabbitmq.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public class CultivationEvent {
    // 수확 완료 알림
    public record HarvestCompletedEvent (
            long userId,
            long cultivationId,
            String cultivationName,
            long harvestId,
            BigDecimal harvestQuantity,
            OffsetDateTime harvestedAt
    ) {}

    // 몇분 이상 센서값이 들어오지 않는 알림
    public record SensorDataUnavailableEvent (
        long cultivationId,
        String deviceName,
        String message,
        OffsetDateTime occurredAt
    ) {}

    // 멤버 초대
    public record CultivationMemberInvitedEvent (
            long cultivationId,
            long inviterId,
            String inviterNickname,
            long inviteeId,
            String inviteeNickname,
            String invitationUrl,
            OffsetDateTime occurredAt
    ) {}
}
