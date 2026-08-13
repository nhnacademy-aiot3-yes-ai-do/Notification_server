package site.yesaido.notification_server.rabbitmq.persistence;

public class RabbitMqNotificationFailureHistoryPersistenceException extends RuntimeException {

    public RabbitMqNotificationFailureHistoryPersistenceException(String message, Throwable persistenceFailureCause) {
        super(message, persistenceFailureCause);
    }
}
