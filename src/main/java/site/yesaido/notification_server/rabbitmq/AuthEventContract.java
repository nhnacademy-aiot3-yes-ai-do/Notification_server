package site.yesaido.notification_server.rabbitmq;

/**
 * Auth service -> Notification service RabbitMQ wire contract.
 * The producer must publish JSON to {@link #QUEUE} with {@link #TYPE_ID_HEADER}.
 */
public final class AuthEventContract {

    public static final String QUEUE = RabbitMQConstants.NOTIFICATION_AUTH_QUEUE;
    public static final String CONTENT_TYPE = "application/json";
    public static final String TYPE_ID_HEADER = "__TypeId__";

    public static final String LOGIN_ATTEMPTED_TYPE_ID = "user.login-attempted";
    public static final String PASSWORD_CHANGE_ATTEMPTED_TYPE_ID = "user.password-change-attempted";
    public static final String ACCOUNT_REACTIVATION_ATTEMPTED_TYPE_ID = "user.account-reactivation-attempted";

    private AuthEventContract() {
    }
}
