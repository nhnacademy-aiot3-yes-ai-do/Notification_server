package site.yesaido.notification_server.rabbitmq.contract;

/** notification_event_type과 subscription_target_type을 잇는 알림 대상 분류 계약이다. */
public enum NotificationTargetType {
    CULTIVATION,
    INQUIRY,
    USER;

    public String code() {
        return name();
    }
}
