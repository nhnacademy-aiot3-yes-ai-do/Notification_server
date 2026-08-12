package site.yesaido.notification_server.rabbitmq;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.rabbit.annotation.RabbitHandler;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.UserEvent;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_AUTH_QUEUE;
import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.NOTIFICATION_INQUIRY_QUEUE;

@Component
public class UserRabbitMQConsumer {

    @RabbitListener(queues = NOTIFICATION_INQUIRY_QUEUE, autoStartup = "false")
    public void consumeInquiry(
            UserEvent.InquirySubmittedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
    ) throws IOException {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (Exception exception) {
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @Component
    @RabbitListener(
            queues = NOTIFICATION_AUTH_QUEUE,
            containerFactory = "authRabbitListenerContainerFactory",
            autoStartup = "false"
    )
    public static class AuthConsumer {

        @RabbitHandler
        public void consume(
                UserEvent.UserLoginAttemptedEvent event,
                Channel channel,
                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
        ) throws IOException {
            acknowledge(channel, deliveryTag);
        }

        @RabbitHandler
        public void consume(
                UserEvent.UserPasswordChangeAttemptedEvent event,
                Channel channel,
                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
        ) throws IOException {
            acknowledge(channel, deliveryTag);
        }

        @RabbitHandler
        public void consume(
                UserEvent.UserAccountReactivationAttemptedEvent event,
                Channel channel,
                @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag
        ) throws IOException {
            acknowledge(channel, deliveryTag);
        }

        private void acknowledge(Channel channel, long deliveryTag) throws IOException {
            try {
                channel.basicAck(deliveryTag, false);
            } catch (Exception exception) {
                channel.basicNack(deliveryTag, false, false);
            }
        }
    }
}
