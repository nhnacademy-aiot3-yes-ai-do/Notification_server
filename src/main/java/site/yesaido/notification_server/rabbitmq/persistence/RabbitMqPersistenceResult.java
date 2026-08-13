package site.yesaido.notification_server.rabbitmq.persistence;

/** RabbitMQ consumer가 ACK 가능한 멱등 저장 결과다. */
public enum RabbitMqPersistenceResult {
    PERSISTED,
    ALREADY_PROCESSED
}
