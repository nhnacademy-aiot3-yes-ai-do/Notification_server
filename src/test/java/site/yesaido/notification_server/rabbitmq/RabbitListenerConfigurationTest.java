package site.yesaido.notification_server.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import site.yesaido.notification_server.rabbitmq.config.RabbitListenerConfig;
import site.yesaido.notification_server.rabbitmq.listener.AiRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.CultivationRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.RuleEngineRabbitMQConsumer;
import site.yesaido.notification_server.rabbitmq.listener.UserRabbitMQConsumer;

class RabbitListenerConfigurationTest {

    @Test
    void nonAuthFactoryCreatesManualAcknowledgementContainer() {
        RabbitListenerConfig config = new RabbitListenerConfig();
        SimpleRabbitListenerContainerFactory factory =
                config.rabbitListenerContainerFactory(mock(ConnectionFactory.class));
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setQueueNames("queue");
        endpoint.setMessageListener(message -> { });

        SimpleMessageListenerContainer container = factory.createListenerContainer(endpoint);

        assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
    }

    @Test
    void everyNewListenerUsesManualFactoryAndStartsWithApplication() {
        listenersOn(RuleEngineRabbitMQConsumer.class, AiRabbitMQConsumer.class,
                CultivationRabbitMQConsumer.class, UserRabbitMQConsumer.class,
                UserRabbitMQConsumer.AuthConsumer.class)
                .forEach(listener -> {
                    assertThat(listener.autoStartup()).isNotEqualTo("false");
                    assertThat(listener.containerFactory()).isIn(
                            "rabbitListenerContainerFactory", "authRabbitListenerContainerFactory");
                });
    }

    private Stream<RabbitListener> listenersOn(Class<?>... types) {
        return Arrays.stream(types)
                .flatMap(type -> Stream.concat(
                        Arrays.stream(type.getDeclaredMethods())
                                .map(method -> method.getAnnotation(RabbitListener.class))
                                .filter(Objects::nonNull),
                        Stream.of(type.getAnnotation(RabbitListener.class)).filter(Objects::nonNull)));
    }
}
