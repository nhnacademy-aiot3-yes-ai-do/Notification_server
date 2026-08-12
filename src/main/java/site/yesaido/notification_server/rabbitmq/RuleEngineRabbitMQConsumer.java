package site.yesaido.notification_server.rabbitmq;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Component
public class RuleEngineRabbitMQConsumer {

    @RabbitListener(queues = NOTIFICATION_THRESHOLD_QUEUE, autoStartup = "false")
    public void consumeThreshold(RuleEngineEvent.ThresholdStatusChangedEvent event,
                                 Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = NOTIFICATION_ACTION_QUEUE, autoStartup = "false")
    public void consumeAction(RuleEngineEvent.AutomationStateChangedEvent event,
                              Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            channel.basicNack(tag, false, false);
        }
    }
}
