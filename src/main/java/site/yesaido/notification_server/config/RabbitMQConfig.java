package site.yesaido.notification_server.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.DLQ_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.DLQ_ROUTING_KEY;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.DLX_NAME;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_ACTION_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_AUTH_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_CULTIVATION_COMPLETE_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_DAILY_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_EXCHANGE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_HARVEST_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_INQUIRY_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_MEMBER_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_SENSOR_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_THRESHOLD_QUEUE;

@Configuration
public class RabbitMQConfig {

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_NAME, true, false);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    @Bean
    public Declarables notificationQueues() {
        List<Declarable> topology = new ArrayList<>();
        List.of(
                        NOTIFICATION_THRESHOLD_QUEUE,
                        NOTIFICATION_ACTION_QUEUE,
                        NOTIFICATION_DAILY_QUEUE,
                        NOTIFICATION_CULTIVATION_COMPLETE_QUEUE,
                        NOTIFICATION_AUTH_QUEUE,
                        NOTIFICATION_INQUIRY_QUEUE,
                        NOTIFICATION_HARVEST_QUEUE,
                        NOTIFICATION_SENSOR_QUEUE,
                        NOTIFICATION_MEMBER_QUEUE)
                .forEach(queueName -> addQueueAndBinding(topology, queueName));
        return new Declarables(topology);
    }

    @Bean
    public Declarables deadLetterTopology() {
        return new Declarables(BindingBuilder.bind(deadLetterQueue())
                .to(deadLetterExchange()));
    }

    private void addQueueAndBinding(List<Declarable> topology, String queueName) {
        Queue queue = QueueBuilder.durable(queueName)
                .deadLetterExchange(DLX_NAME)
                .deadLetterRoutingKey(DLQ_ROUTING_KEY)
                .build();
        topology.add(queue);
        topology.add(BindingBuilder.bind(queue).to(notificationExchange()).with(queueName));
    }
}
