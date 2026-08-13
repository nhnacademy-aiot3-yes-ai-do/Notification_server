package site.yesaido.notification_server.rabbitmq.exception;

public class RabbitMqNotificationFailureHistoryPersistenceException extends RuntimeException {

    public RabbitMqNotificationFailureHistoryPersistenceException(String message, Throwable persistenceFailureCause) {
        super(message, persistenceFailureCause);
    }
}
