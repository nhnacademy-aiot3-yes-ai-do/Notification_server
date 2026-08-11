package site.yesaido.notification_server.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.springframework.amqp.core.FanoutExchange;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

class NotificationRabbitConfigTest {

    private final NotificationRabbitConfig config = new NotificationRabbitConfig();

    @Test
    void notificationExchange에_용도별_queue_8개를_선언한다() {
        DirectExchange exchange = config.notificationEventExchange();

        Declarables topology = config.notificationEventQueues(exchange);

        assertThat(exchange.getName()).isEqualTo("yes-nhn.notification.exchange");
        assertThat(exchange.getType()).isEqualTo("direct");
        assertThat(topology.getDeclarables())
                .filteredOn(Queue.class::isInstance)
                .map(Queue.class::cast)
                .extracting(Queue::getName)
                .containsExactlyInAnyOrder(
                        "yes-nhn.notification.threshold.queue",
                        "yes-nhn.notification.action.queue",
                        "yes-nhn.notification.daily.queue",
                        "yes-nhn.notification.login.queue",
                        "yes-nhn.notification.question.queue",
                        "yes-nhn.notification.answer.queue",
                        "yes-nhn.notification.done.queue",
                        "yes-nhn.notification.cultivation-finished.queue");
    }

    @Test
    void deadLetterExchange는_fanout으로_선언한다() {
        FanoutExchange deadLetterExchange = config.notificationDeadLetterExchange();

        assertThat(deadLetterExchange.getName()).isEqualTo("yes-nhn.dlx");
        assertThat(deadLetterExchange.getType()).isEqualTo("fanout");
    }

    @Test
    void notificationQueue는_같은이름의_routingKey로_directExchange에_연결한다() {
        DirectExchange exchange = config.notificationEventExchange();
        Declarables topology = config.notificationEventQueues(exchange);

        assertThat(topology.getDeclarables())
                .filteredOn(Binding.class::isInstance)
                .map(Binding.class::cast)
                .allSatisfy(binding -> {
                    assertThat(binding.getExchange()).isEqualTo(NotificationRabbitConstants.EVENT_EXCHANGE);
                    assertThat(binding.getRoutingKey()).isEqualTo(binding.getDestination());
                });
    }
}
