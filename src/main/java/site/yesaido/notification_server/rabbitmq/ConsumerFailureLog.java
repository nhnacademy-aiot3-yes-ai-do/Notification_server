package site.yesaido.notification_server.rabbitmq;

import java.util.UUID;
import lombok.extern.slf4j.Slf4j;

/** RabbitMQ 소비 실패를 민감한 payload 없이 일관된 필드로 기록한다. */
@Slf4j
public final class ConsumerFailureLog {

    private ConsumerFailureLog() {
    }

    public static void error(String queue, UUID eventId, long deliveryTag, Exception exception) {
        log.error("RABBITMQ_CONSUMER_FAILURE queue={}, eventId={}, deliveryTag={}, failureType={}",
                queue, eventId, deliveryTag, exception.getClass().getSimpleName(), exception);
    }
}
