package site.yesaido.notification_server.config;

import static org.assertj.core.api.Assertions.assertThat;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.DLQ_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.DLQ_ROUTING_KEY;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.DLX_NAME;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_ACTION_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_AUTH_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_CULTIVATION_COMPLETE_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_DAILY_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_HARVEST_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_INQUIRY_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_MEMBER_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_SENSOR_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_THRESHOLD_QUEUE;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;

class RabbitMQConfigTopologyTest {

    private final RabbitMQConfig config = new RabbitMQConfig();

    @Test
    void fanoutDlx를선언한다() {
        FanoutExchange dlx = config.deadLetterExchange();
        Queue dlq = config.deadLetterQueue();

        assertThat(dlx.getName()).isEqualTo(DLX_NAME);
        assertThat(dlx.getType()).isEqualTo("fanout");
        assertThat(dlq.getName()).isEqualTo(DLQ_QUEUE);
        assertThat(config.deadLetterTopology().getDeclarables())
                .anyMatch(declarable -> declarable instanceof Binding binding
                        && binding.getExchange().equals(DLX_NAME)
                        && binding.getRoutingKey().isEmpty());
    }

    @Test
    void 새Consumer의모든Queue를Dlx와DlqRoutingKey로선언한다() {
        List<Queue> queues = config.notificationQueues().getDeclarables().stream()
                .filter(Queue.class::isInstance)
                .map(Queue.class::cast)
                .toList();

        assertThat(queues)
                .extracting(Queue::getName)
                .containsExactlyInAnyOrder(
                        NOTIFICATION_THRESHOLD_QUEUE,
                        NOTIFICATION_ACTION_QUEUE,
                        NOTIFICATION_DAILY_QUEUE,
                        NOTIFICATION_CULTIVATION_COMPLETE_QUEUE,
                        NOTIFICATION_AUTH_QUEUE,
                        NOTIFICATION_INQUIRY_QUEUE,
                        NOTIFICATION_HARVEST_QUEUE,
                        NOTIFICATION_SENSOR_QUEUE,
                        NOTIFICATION_MEMBER_QUEUE);
        assertThat(queues).allMatch(queue ->
                DLX_NAME.equals(queue.getArguments().get("x-dead-letter-exchange"))
                        && DLQ_ROUTING_KEY.equals(queue.getArguments().get("x-dead-letter-routing-key")));
    }
}
