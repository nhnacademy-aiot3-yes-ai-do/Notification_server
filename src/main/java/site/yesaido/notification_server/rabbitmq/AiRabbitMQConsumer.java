package site.yesaido.notification_server.rabbitmq;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Component
public class AiRabbitMQConsumer {

    @RabbitListener(queues = NOTIFICATION_DAILY_QUEUE, autoStartup = "false")
    public void consumeDaily(AiEvent.DailyFeedbackGeneratedEvent event,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            channel.basicNack(tag, false, false);
        }
    }
    
    @RabbitListener(queues = NOTIFICATION_CULTIVATION_COMPLETE_QUEUE, autoStartup = "false")
    public void consumeCultivationComplete(AiEvent.CultivationCompletedEvent event, Channel channel, @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            channel.basicNack(tag, false, false);
        }
    }
}
