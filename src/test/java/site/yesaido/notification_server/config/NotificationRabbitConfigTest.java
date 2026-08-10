package site.yesaido.notification_server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.springframework.amqp.core.Binding;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
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
                        NotificationRabbitConstants.THRESHOLD_QUEUE,
                        NotificationRabbitConstants.ACTION_QUEUE,
                        NotificationRabbitConstants.DAILY_QUEUE,
                        NotificationRabbitConstants.LOGIN_QUEUE,
                        NotificationRabbitConstants.QUESTION_QUEUE,
                        NotificationRabbitConstants.ANSWER_QUEUE,
                        NotificationRabbitConstants.HARVEST_QUEUE,
                        NotificationRabbitConstants.CULTIVATION_FINISHED_QUEUE);
        assertThat(topology.getDeclarables())
                .filteredOn(Queue.class::isInstance)
                .map(Queue.class::cast)
                .allSatisfy(queue -> assertThat(queue.getArguments())
                        .containsEntry("x-dead-letter-exchange",
                                NotificationRabbitConstants.DEAD_LETTER_EXCHANGE)
                        .doesNotContainKey("x-dead-letter-routing-key"));
    }

    @Test
    void notificationDlx는_공용Dlq로_보내는_fanoutExchange다() {
        FanoutExchange deadLetterExchange = config.notificationDeadLetterExchange();
        Queue deadLetterQueue = config.notificationDeadLetterQueue();
        Binding binding = config.notificationDeadLetterBinding(deadLetterQueue, deadLetterExchange);

        assertThat(deadLetterExchange.getName())
                .isEqualTo(NotificationRabbitConstants.DEAD_LETTER_EXCHANGE);
        assertThat(deadLetterExchange.getType()).isEqualTo("fanout");
        assertThat(deadLetterQueue.getName()).isEqualTo(NotificationRabbitConstants.DEAD_LETTER_QUEUE);
        assertThat(binding.getExchange()).isEqualTo(NotificationRabbitConstants.DEAD_LETTER_EXCHANGE);
    }
}
