package site.yesaido.notification_server.rabbitmq.event;

import java.time.OffsetDateTime;
import java.util.List;

public class UserEvent {
    // 로그인 성공 여부
    public record UserLoginAttemptedEvent (
            long userId,
            String nickname,
            boolean succeeded,
            String loginLocation,
            OffsetDateTime occurredAt
    ) {}

    // 비밀번호 변경
    public record UserPasswordChangeAttemptedEvent (
            long userId,
            String nickname,
            boolean succeeded,
            OffsetDateTime occurredAt
    ) {}

    // 휴먼 계정 해제 알림
    public record UserAccountReactivationAttemptedEvent (
            long userId,
            String nickname,
            boolean succeeded,
            OffsetDateTime occurredAt
    ) {}

    // 문의 사항
    public record InquirySubmittedEvent (
            long sendUserId,
            List<Long> receiveUserIds, // 관리자들에게 보내야 할때 List, 응답이면 그냥 단일
            long inquiryId,
            String title,
            String category,
            String inquiryUrl,
            InquiryType inquiryType,
            OffsetDateTime occurredAt
    ) {}

    public enum InquiryType {
        QUESTION, ANSWER
    }
}
