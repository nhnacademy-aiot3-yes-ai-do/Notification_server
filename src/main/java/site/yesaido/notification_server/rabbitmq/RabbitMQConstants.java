package site.yesaido.notification_server.rabbitmq;

public final class RabbitMQConstants {

    private RabbitMQConstants() {
    }

    // Dead Letter
    public static final String DLX_NAME = "yes-nhn.dlx";
    public static final String DLX_KEY = "x-dead-letter-exchange";
    public static final String DLQ_QUEUE = "yes-nhn.dlq";

    // Exchange
    public static final String NOTIFICATION_EXCHANGE = "yes-nhn.notification.exchange";

    // Rule Engine
    public static final String NOTIFICATION_THRESHOLD_QUEUE = "yes-nhn.notification.threshold.queue";
    public static final String NOTIFICATION_ACTION_QUEUE = "yes-nhn.notification.action.queue";

    // AI
    public static final String NOTIFICATION_DAILY_QUEUE = "yes-nhn.notification.daily.queue";
    public static final String NOTIFICATION_CULTIVATION_COMPLETE_QUEUE = "yes-nhn.notification.cultivation-complete.queue";

    // User
    public static final String NOTIFICATION_AUTH_QUEUE = "yes-nhn.notification.auth.queue";
    public static final String NOTIFICATION_INQUIRY_QUEUE = "yes-nhn.notification.inquiry.queue";

    // Cultivation
    public static final String NOTIFICATION_HARVEST_QUEUE = "yes-nhn.notification.harvest.queue";
    public static final String NOTIFICATION_SENSOR_QUEUE = "yes-nhn.notification.sensor.queue";
    public static final String NOTIFICATION_MEMBER_QUEUE = "yes-nhn.notification.member.queue";
}
