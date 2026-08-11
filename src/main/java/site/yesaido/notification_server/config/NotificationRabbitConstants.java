package site.yesaido.notification_server.config;

import java.util.List;

public final class NotificationRabbitConstants {

    public static final String EVENT_EXCHANGE = "yes-nhn.notification.exchange";
    public static final String DEAD_LETTER_EXCHANGE = "yes-nhn.dlx";
    public static final String DEAD_LETTER_QUEUE = "yes-nhn.dlq";

    public static final String THRESHOLD_QUEUE = "yes-nhn.notification.threshold.queue";
    public static final String ACTION_QUEUE = "yes-nhn.notification.action.queue";
    public static final String DAILY_QUEUE = "yes-nhn.notification.daily.queue";
    public static final String LOGIN_QUEUE = "yes-nhn.notification.login.queue";
    public static final String QUESTION_QUEUE = "yes-nhn.notification.question.queue";
    public static final String ANSWER_QUEUE = "yes-nhn.notification.answer.queue";
    public static final String HARVEST_QUEUE = "yes-nhn.notification.done.queue";
    public static final String CULTIVATION_FINISHED_QUEUE =
            "yes-nhn.notification.cultivation-finished.queue";

    public static final List<String> EVENT_QUEUES = List.of(
            THRESHOLD_QUEUE,
            ACTION_QUEUE,
            DAILY_QUEUE,
            LOGIN_QUEUE,
            QUESTION_QUEUE,
            ANSWER_QUEUE,
            HARVEST_QUEUE,
            CULTIVATION_FINISHED_QUEUE);

    private NotificationRabbitConstants() {
    }
}
