package site.yesaido.notification_server.config;

import java.util.Map;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(NotificationProperties.class)
public class NotificationRabbitConfig {

    @Bean
    TopicExchange notificationEventExchange(NotificationProperties properties) {
        return new TopicExchange(properties.rabbit().exchange(), true, false);
    }

    @Bean
    TopicExchange notificationDeadLetterExchange(NotificationProperties properties) {
        return new TopicExchange(properties.rabbit().deadLetterExchange(), true, false);
    }

    @Bean
    Queue notificationEventQueue(NotificationProperties properties) {
        Map<String, Object> arguments = Map.of(
                "x-dead-letter-exchange", properties.rabbit().deadLetterExchange(),
                "x-dead-letter-routing-key", properties.rabbit().deadLetterRoutingKey());
        return new Queue(properties.rabbit().queue(), true, false, false, arguments);
    }

    @Bean
    Queue notificationDeadLetterQueue(NotificationProperties properties) {
        return new Queue(properties.rabbit().deadLetterQueue(), true);
    }

    @Bean
    Binding notificationEventBinding(
            Queue notificationEventQueue,
            TopicExchange notificationEventExchange,
            NotificationProperties properties
    ) {
        return BindingBuilder.bind(notificationEventQueue)
                .to(notificationEventExchange)
                .with(properties.rabbit().routingKey());
    }

    @Bean
    Binding notificationDeadLetterBinding(
            Queue notificationDeadLetterQueue,
            TopicExchange notificationDeadLetterExchange,
            NotificationProperties properties
    ) {
        return BindingBuilder.bind(notificationDeadLetterQueue)
                .to(notificationDeadLetterExchange)
                .with(properties.rabbit().deadLetterRoutingKey());
    }
}
