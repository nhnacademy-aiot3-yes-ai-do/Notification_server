package site.yesaido.notification_server.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Objects;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import site.yesaido.notification_server.rabbitmq.listener.AiRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.CultivationRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.RuleEngineRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.UserRabbitMQConsumer;

class NotificationRabbitMQConsumerClassificationTest {

    @Test
    void 도메인_consumer는_큐별_listener를_가진다() {
        assertThat(methodQueues(RuleEngineRabbitMQConsumer.class))
                .containsExactlyInAnyOrder("yes-nhn.notification.threshold.queue", "yes-nhn.notification.action.queue");
        assertThat(methodQueues(AiRabbitMQConsumer.class))
                .containsExactlyInAnyOrder("yes-nhn.notification.daily.queue", "yes-nhn.notification.cultivation-complete.queue");
        assertThat(methodQueues(CultivationRabbitMQConsumer.class))
                .containsExactlyInAnyOrder("yes-nhn.notification.harvest.queue", "yes-nhn.notification.sensor.queue", "yes-nhn.notification.member.queue");
        assertThat(UserRabbitMQConsumer.AuthConsumer.class.getAnnotation(RabbitListener.class).queues())
                .containsExactly("yes-nhn.notification.auth.queue");
        assertThat(methodQueues(UserRabbitMQConsumer.class))
                .containsExactly("yes-nhn.notification.inquiry.queue");
    }

    private String[] methodQueues(Class<?> consumerType) {
        return Arrays.stream(consumerType.getDeclaredMethods())
                .map(method -> method.getAnnotation(RabbitListener.class))
                .filter(Objects::nonNull)
                .flatMap(annotation -> Arrays.stream(annotation.queues()))
                .toArray(String[]::new);
    }
}
