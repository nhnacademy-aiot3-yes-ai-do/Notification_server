package site.yesaido.notification_server.config;

public final class NotificationRabbitConstants {

    public static final String EVENT_EXCHANGE = "yes-nhn.notification.exchange";

    public static final String THRESHOLD_QUEUE = "yes-nhn.notification.threshold.queue";
    public static final String THRESHOLD_ROUTING_KEY = THRESHOLD_QUEUE;
    public static final String ACTION_QUEUE = "yes-nhn.notification.action.queue";
    public static final String ACTION_ROUTING_KEY = ACTION_QUEUE;
    public static final String DAILY_QUEUE = "yes-nhn.notification.daily.queue";
    public static final String DAILY_ROUTING_KEY = DAILY_QUEUE;
    public static final String LOGIN_QUEUE = "yes-nhn.notification.login.queue";
    public static final String LOGIN_ROUTING_KEY = LOGIN_QUEUE;
    public static final String QUESTION_QUEUE = "yes-nhn.notification.question.queue";
    public static final String QUESTION_ROUTING_KEY = QUESTION_QUEUE;
    public static final String ANSWER_QUEUE = "yes-nhn.notification.answer.queue";
    public static final String ANSWER_ROUTING_KEY = ANSWER_QUEUE;
    public static final String HARVEST_QUEUE = "yes-nhn.notification.harvest.queue";
    public static final String HARVEST_ROUTING_KEY = HARVEST_QUEUE;
    public static final String CULTIVATION_FINISHED_QUEUE =
            "yes-nhn.notification.cultivation-finished.queue";
    public static final String CULTIVATION_FINISHED_ROUTING_KEY = CULTIVATION_FINISHED_QUEUE;

    public static final String DEAD_LETTER_EXCHANGE = "yes-nhn.dlx";
    public static final String DEAD_LETTER_QUEUE = "yes-nhn.dlq";

    private NotificationRabbitConstants() {
    }
}
