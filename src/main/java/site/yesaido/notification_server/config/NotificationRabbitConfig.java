package site.yesaido.notification_server.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.FanoutExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationRabbitConfig {

    private static final List<QueueRoute> EVENT_ROUTES = List.of(
            new QueueRoute(NotificationRabbitConstants.THRESHOLD_QUEUE,
                    NotificationRabbitConstants.THRESHOLD_ROUTING_KEY),
            new QueueRoute(NotificationRabbitConstants.ACTION_QUEUE,
                    NotificationRabbitConstants.ACTION_ROUTING_KEY),
            new QueueRoute(NotificationRabbitConstants.DAILY_QUEUE,
                    NotificationRabbitConstants.DAILY_ROUTING_KEY),
            new QueueRoute(NotificationRabbitConstants.LOGIN_QUEUE,
                    NotificationRabbitConstants.LOGIN_ROUTING_KEY),
            new QueueRoute(NotificationRabbitConstants.QUESTION_QUEUE,
                    NotificationRabbitConstants.QUESTION_ROUTING_KEY),
            new QueueRoute(NotificationRabbitConstants.ANSWER_QUEUE,
                    NotificationRabbitConstants.ANSWER_ROUTING_KEY),
            new QueueRoute(NotificationRabbitConstants.HARVEST_QUEUE,
                    NotificationRabbitConstants.HARVEST_ROUTING_KEY),
            new QueueRoute(NotificationRabbitConstants.CULTIVATION_FINISHED_QUEUE,
                    NotificationRabbitConstants.CULTIVATION_FINISHED_ROUTING_KEY));

    @Bean
    DirectExchange notificationEventExchange() {
        return new DirectExchange(NotificationRabbitConstants.EVENT_EXCHANGE, true, false);
    }

    @Bean
    FanoutExchange notificationDeadLetterExchange() {
        return new FanoutExchange(NotificationRabbitConstants.DEAD_LETTER_EXCHANGE, true, false);
    }

    @Bean
    Declarables notificationEventQueues(DirectExchange notificationEventExchange) {
        List<Declarable> declarables = new ArrayList<>();
        Map<String, Object> arguments = Map.of(
                "x-dead-letter-exchange", NotificationRabbitConstants.DEAD_LETTER_EXCHANGE);

        for (QueueRoute route : EVENT_ROUTES) {
            Queue queue = new Queue(route.queue(), true, false, false, arguments);
            Binding binding = BindingBuilder.bind(queue)
                    .to(notificationEventExchange)
                    .with(route.routingKey());
            declarables.add(queue);
            declarables.add(binding);
        }
        return new Declarables(declarables);
    }

    @Bean
    Queue notificationDeadLetterQueue() {
        return new Queue(NotificationRabbitConstants.DEAD_LETTER_QUEUE, true);
    }

    @Bean
    Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            FanoutExchange notificationDeadLetterExchange
    ) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange);
    }

    private record QueueRoute(String queue, String routingKey) {
    }
}
