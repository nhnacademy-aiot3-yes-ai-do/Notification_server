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

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Configuration
public class RabbitMQConfig {

    @Bean
    public FanoutExchange deadLetterExchange() {
        return new FanoutExchange(DLX_NAME);
    }

    @Bean
    public Queue deadLetterQueue() {
        return QueueBuilder.durable(DLQ_QUEUE).build();
    }

    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE);
    }

    @Bean
    public Declarables notificationQueues() {
        List<Declarable> topology = new ArrayList<>();
        addQueueAndBinding(topology, NOTIFICATION_THRESHOLD_QUEUE);
        addQueueAndBinding(topology, NOTIFICATION_ACTION_QUEUE);
        addQueueAndBinding(topology, NOTIFICATION_DAILY_QUEUE);
        addQueueAndBinding(topology, NOTIFICATION_CULTIVATION_COMPLETE_QUEUE);
        addQueueAndBinding(topology, NOTIFICATION_AUTH_QUEUE);
        addQueueAndBinding(topology, NOTIFICATION_INQUIRY_QUEUE);
        addQueueAndBinding(topology, NOTIFICATION_HARVEST_QUEUE);
        addQueueAndBinding(topology, NOTIFICATION_SENSOR_QUEUE);
        addQueueAndBinding(topology, NOTIFICATION_MEMBER_QUEUE);
        return new Declarables(topology);
    }

    @Bean
    public Declarables deadLetterTopology() {
        return new Declarables(BindingBuilder.bind(deadLetterQueue()).to(deadLetterExchange()));
    }

    private void addQueueAndBinding(List<Declarable> topology, String queueName) {
        Queue queue = QueueBuilder.durable(queueName)
                .withArgument(DLX_KEY, DLX_NAME)
                .build();
        topology.add(queue);
        topology.add(BindingBuilder.bind(queue).to(notificationExchange()).with(queueName));
    }
}
