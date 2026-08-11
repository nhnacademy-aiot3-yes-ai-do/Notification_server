package site.yesaido.notification_server.event;

import java.time.OffsetDateTime;

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
            long userId,
            long inquiryId,
            String title,
            String category,
            String inquiryUrl,
            String inquiryType, // enum 으로
            OffsetDateTime occurredAt
    ) {}
}
