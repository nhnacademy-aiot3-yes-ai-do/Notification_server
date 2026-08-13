package site.yesaido.notification_server.rabbitmq;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerEndpoint;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.test.util.ReflectionTestUtils;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
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
                config.rabbitListenerContainerFactory(
                        mock(ConnectionFactory.class), config.nonAuthEventMessageConverter());
        SimpleRabbitListenerEndpoint endpoint = new SimpleRabbitListenerEndpoint();
        endpoint.setQueueNames("queue");
        endpoint.setMessageListener(message -> { });

        SimpleMessageListenerContainer container = factory.createListenerContainer(endpoint);

        assertThat(container.getAcknowledgeMode()).isEqualTo(AcknowledgeMode.MANUAL);
    }

    @Test
    void nonAuthFactoryUsesJsonConverterForListenerEventRecords() {
        RabbitListenerConfig config = new RabbitListenerConfig();
        SimpleRabbitListenerContainerFactory factory = config.rabbitListenerContainerFactory(
                mock(ConnectionFactory.class), config.nonAuthEventMessageConverter());

        assertThat(ReflectionTestUtils.getField(factory, "messageConverter"))
                .isInstanceOf(JacksonJsonMessageConverter.class);
    }

    @Test
    void nonAuthJsonConverterUsesListenerInferredRecordType() {
        RabbitListenerConfig config = new RabbitListenerConfig();
        JacksonJsonMessageConverter converter = config.nonAuthEventMessageConverter();
        MessageProperties properties = new MessageProperties();
        properties.setContentType(MessageProperties.CONTENT_TYPE_JSON);
        properties.setInferredArgumentType(CultivationEvent.HarvestCompletedEvent.class);
        Message message = new Message(("""
                {"eventId":"7a5bc0a0-b4a7-4c50-b2e2-4d238c234487","userId":2,
                 "cultivationId":7,"cultivationName":"토마토 A동","harvestId":8,
                 "harvestQuantity":12.50,"harvestedAt":"2026-08-12T10:15:30+09:00"}
                """).getBytes(StandardCharsets.UTF_8), properties);

        Object converted = converter.fromMessage(message);

        assertThat(converted).isInstanceOf(CultivationEvent.HarvestCompletedEvent.class);
        CultivationEvent.HarvestCompletedEvent event = (CultivationEvent.HarvestCompletedEvent) converted;
        assertThat(event.harvestQuantity()).isEqualByComparingTo(new BigDecimal("12.50"));
        assertThat(event.harvestedAt()).isEqualTo(OffsetDateTime.parse("2026-08-12T10:15:30+09:00"));
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
