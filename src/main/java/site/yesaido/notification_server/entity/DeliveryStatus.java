package site.yesaido.notification_server.entity;

/** 외부 채널 발송의 생명주기 상태. DB에는 이름을 그대로 저장한다. */
public enum DeliveryStatus {
    CREATED,
    PENDING,
    SENDING,
    SENT,
    FAILED
}
