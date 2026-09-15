package site.yesaido.notification_server.config;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import site.yesaido.common.rabbitmq.DeadLetterQueues;
import site.yesaido.common.rabbitmq.DeadLetterTopologyConfiguration;
import site.yesaido.common.rabbitmq.RabbitDeadLetterProperties;

import java.util.ArrayList;
import java.util.List;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Configuration
@Import(DeadLetterTopologyConfiguration.class)
public class RabbitMQConfig {
    @Bean
    public DirectExchange notificationExchange() {
        return new DirectExchange(NOTIFICATION_EXCHANGE, true, false);
    }

    @Bean
    public Declarables notificationQueues(RabbitDeadLetterProperties dlProps) {
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
                .forEach(queueName -> addQueueAndBinding(topology, queueName, dlProps));
        return new Declarables(topology);
    }

    private void addQueueAndBinding(List<Declarable> topology, String queueName, RabbitDeadLetterProperties dlProps) {
        Queue queue = DeadLetterQueues.durableWithDeadLetter(queueName, dlProps).build();
        topology.add(queue);
        topology.add(BindingBuilder.bind(queue).to(notificationExchange()).with(queueName));
    }
}
