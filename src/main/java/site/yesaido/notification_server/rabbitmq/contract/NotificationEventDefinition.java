package site.yesaido.notification_server.rabbitmq.contract;

/**
 * 신규 RabbitMQ 알림 경로가 처리할 수 있는 이벤트와 DB 기준 target type의 고정 계약이다.
 * code는 notification_event_type.code, targetType은 subscription_target_type.target_type과 일치해야 한다.
 */
public enum NotificationEventDefinition {
    ENVIRONMENT_THRESHOLD_BREACHED("ENVIRONMENT_THRESHOLD_BREACHED", NotificationTargetType.CULTIVATION),
    ENVIRONMENT_RECOVERED("ENVIRONMENT_RECOVERED", NotificationTargetType.CULTIVATION),
    ACTUATOR_CONTROL_FAILED("ACTUATOR_CONTROL_FAILED", NotificationTargetType.CULTIVATION),
    ACTUATOR_CONTROL_SUCCEEDED("ACTUATOR_CONTROL_SUCCEEDED", NotificationTargetType.CULTIVATION),
    DAILY_FEEDBACK_COMPLETED("DAILY_FEEDBACK_COMPLETED", NotificationTargetType.CULTIVATION),
    CULTIVATION_FINISHED("CULTIVATION_FINISHED", NotificationTargetType.CULTIVATION),
    HARVEST_COMPLETED("HARVEST_COMPLETED", NotificationTargetType.CULTIVATION),
    SENSOR_OFFLINE("SENSOR_OFFLINE", NotificationTargetType.CULTIVATION),
    CULTIVATION_MEMBER_INVITED("CULTIVATION_MEMBER_INVITED", NotificationTargetType.CULTIVATION),
    MEMBER_ADDED("MEMBER_ADDED", NotificationTargetType.USER),
    INQUIRY_SUBMITTED("INQUIRY_SUBMITTED", NotificationTargetType.INQUIRY),
    INQUIRY_ANSWERED("INQUIRY_ANSWERED", NotificationTargetType.INQUIRY),
    LOGIN_SUCCEEDED("LOGIN_SUCCEEDED", NotificationTargetType.USER),
    LOGIN_FAILED("LOGIN_FAILED", NotificationTargetType.USER),
    PASSWORD_CHANGED("PASSWORD_CHANGED", NotificationTargetType.USER),
    PASSWORD_CHANGE_FAILED("PASSWORD_CHANGE_FAILED", NotificationTargetType.USER),
    ACCOUNT_REACTIVATED("ACCOUNT_REACTIVATED", NotificationTargetType.USER),
    ACCOUNT_REACTIVATION_FAILED("ACCOUNT_REACTIVATION_FAILED", NotificationTargetType.USER);

    private final String code;
    private final NotificationTargetType targetType;

    NotificationEventDefinition(String code, NotificationTargetType targetType) {
        this.code = code;
        this.targetType = targetType;
    }

    public String code() {
        return code;
    }

    public String targetType() {
        return targetType.code();
    }
}
