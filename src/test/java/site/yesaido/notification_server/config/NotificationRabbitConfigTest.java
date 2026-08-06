package site.yesaido.notification_server.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;

class NotificationRabbitConfigTest {

    private final NotificationRabbitConfig config = new NotificationRabbitConfig();

    @Test
    void notificationExchange에_용도별_queue_8개를_선언한다() {
        NotificationProperties properties = properties();
        DirectExchange exchange = config.notificationEventExchange(properties);

        Declarables topology = config.notificationEventQueues(exchange, properties);

        assertThat(exchange.getName()).isEqualTo("yes-nhn.notification.exchange");
        assertThat(exchange.getType()).isEqualTo("direct");
        assertThat(topology.getDeclarables())
                .filteredOn(Queue.class::isInstance)
                .map(Queue.class::cast)
                .extracting(Queue::getName)
                .containsExactlyInAnyOrder(
                        "threshold.queue",
                        "action.queue",
                        "daily.queue",
                        "login.queue",
                        "question.queue",
                        "answer.queue",
                        "harvest.queue",
                        "cultivation-finished.queue");
    }

    private NotificationProperties properties() {
        return new NotificationProperties(
                new NotificationProperties.Rabbit(
                        "yes-nhn.notification.exchange",
                        route("threshold"),
                        route("action"),
                        route("daily"),
                        route("login"),
                        route("question"),
                        route("answer"),
                        route("harvest"),
                        route("cultivation-finished"),
                        "yes-nhn.dlx",
                        "yes-nhn.dlq",
                        "yes-nhn.dlq"),
                new NotificationProperties.Provider(
                        new NotificationProperties.Telegram("https://api.telegram.org", ""),
                        new NotificationProperties.Discord(List.of("discord.com"))),
                new NotificationProperties.Retry(java.time.Duration.ZERO));
    }

    private NotificationProperties.EventRoute route(String name) {
        return new NotificationProperties.EventRoute(name + ".queue", name + ".routing-key");
    }
}
