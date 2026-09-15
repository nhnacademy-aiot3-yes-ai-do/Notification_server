package site.yesaido.notification_server.config;

import static org.assertj.core.api.Assertions.assertThat;
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
import site.yesaido.common.rabbitmq.DeadLetterTopologyConfiguration;
import site.yesaido.common.rabbitmq.RabbitDeadLetterProperties;

class RabbitMQConfigTopologyTest {

    private final RabbitMQConfig config = new RabbitMQConfig();
    private final RabbitDeadLetterProperties dlProps = new RabbitDeadLetterProperties();

    @Test
    void fanoutDlx를선언한다() {
        DeadLetterTopologyConfiguration dlTopology = new DeadLetterTopologyConfiguration();

        FanoutExchange dlx = dlTopology.deadLetterExchange(dlProps);
        Queue dlq = dlTopology.deadLetterQueue(dlProps);
        Binding binding = dlTopology.deadLetterBinding(dlq, dlx);

        assertThat(dlx.getName()).isEqualTo(dlProps.getExchangeName());
        assertThat(dlx.getType()).isEqualTo("fanout");
        assertThat(dlq.getName()).isEqualTo(dlProps.getQueueName());
        assertThat(binding.getExchange()).isEqualTo(dlProps.getExchangeName());
        assertThat(binding.getRoutingKey()).isEmpty();
    }

    @Test
    void 새Consumer의모든Queue를Dlx로선언한다() {
        List<Queue> queues = config.notificationQueues(dlProps).getDeclarables().stream()
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
                dlProps.getExchangeName().equals(queue.getArguments().get("x-dead-letter-exchange")));
    }
}