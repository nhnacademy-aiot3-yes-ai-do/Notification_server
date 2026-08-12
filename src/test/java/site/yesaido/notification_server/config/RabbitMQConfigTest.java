package site.yesaido.notification_server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;

class RabbitMQConfigTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void 이벤트_도메인별_큐_9개를_선언한다() {
        Declarables topology = config.notificationQueues();

        assertThat(topology.getDeclarables())
                .filteredOn(Queue.class::isInstance)
                .map(Queue.class::cast)
                .extracting(Queue::getName)
                .containsExactlyInAnyOrderElementsOf(List.of(
                        "yes-nhn.notification.threshold.queue",
                        "yes-nhn.notification.action.queue",
                        "yes-nhn.notification.daily.queue",
                        "yes-nhn.notification.cultivation-complete.queue",
                        "yes-nhn.notification.auth.queue",
                        "yes-nhn.notification.inquiry.queue",
                        "yes-nhn.notification.harvest.queue",
                        "yes-nhn.notification.sensor.queue",
                        "yes-nhn.notification.member.queue"));
    }
}
