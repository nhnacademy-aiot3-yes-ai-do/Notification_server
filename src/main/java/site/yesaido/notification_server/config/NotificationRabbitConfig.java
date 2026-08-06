package site.yesaido.notification_server.config;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarable;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationRabbitConfig {

    @Bean
    DirectExchange notificationEventExchange(NotificationProperties properties) {
        return new DirectExchange(properties.rabbit().exchange(), true, false);
    }

    @Bean
    DirectExchange notificationDeadLetterExchange(NotificationProperties properties) {
        return new DirectExchange(properties.rabbit().deadLetterExchange(), true, false);
    }

    @Bean
    Declarables notificationEventQueues(
            DirectExchange notificationEventExchange,
            NotificationProperties properties
    ) {
        List<Declarable> declarables = new ArrayList<>();
        Map<String, Object> arguments = Map.of(
                "x-dead-letter-exchange", properties.rabbit().deadLetterExchange(),
                "x-dead-letter-routing-key", properties.rabbit().deadLetterRoutingKey());

        for (NotificationProperties.EventRoute route : properties.rabbit().eventRoutes()) {
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
    Queue notificationDeadLetterQueue(NotificationProperties properties) {
        return new Queue(properties.rabbit().deadLetterQueue(), true);
    }

    @Bean
    Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            DirectExchange notificationDeadLetterExchange,
            NotificationProperties properties
    ) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(properties.rabbit().deadLetterRoutingKey());
    }
}
