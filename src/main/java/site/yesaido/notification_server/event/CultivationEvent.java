package site.yesaido.notification_server.event;

import java.time.OffsetDateTime;

public class CultivationEvent {
    // 수확 완료 알림
    public record HarvestCompletedEvent (
            long userId,
            long cultivationId,
            String cultivationName,
            long harvestId,
            long harvestQuantity,
            OffsetDateTime harvestedAt
    ) {}

    // 몇분 이상 센서값이 들어오지 않는 알림
    public record SensorDataUnavailableEvent (
        long cultivationId,
        String deviceName,
        String message
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
