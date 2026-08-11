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
        Map<String, Object> arguments = Map.of(
                "x-dead-letter-exchange", NotificationRabbitConstants.DEAD_LETTER_EXCHANGE,
                "x-dead-letter-routing-key", NotificationRabbitConstants.DEAD_LETTER_QUEUE);
        List<Declarable> declarables = new ArrayList<>();
        for (String queueName : NotificationRabbitConstants.EVENT_QUEUES) {
            Queue queue = new Queue(queueName, true, false, false, arguments);
            declarables.add(queue);
            declarables.add(BindingBuilder.bind(queue)
                    .to(notificationEventExchange)
                    .with(queueName));
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
}
