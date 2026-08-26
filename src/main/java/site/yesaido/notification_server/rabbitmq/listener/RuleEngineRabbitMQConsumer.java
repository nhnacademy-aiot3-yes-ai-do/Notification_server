package site.yesaido.notification_server.rabbitmq.listener;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.RuleEngineEvent;
import site.yesaido.notification_server.rabbitmq.facade.RabbitMqNotificationFacade;
import site.yesaido.notification_server.rabbitmq.ConsumerFailureLog;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Component
@RequiredArgsConstructor
public class RuleEngineRabbitMQConsumer {

    private final RabbitMqNotificationFacade notificationFacade;

    @RabbitListener(queues = NOTIFICATION_THRESHOLD_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeThreshold(RuleEngineEvent.ThresholdStatusChangedEvent event,
                                 Channel channel,
                                 @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        process(event, channel, tag);
    }

    @RabbitListener(queues = NOTIFICATION_ACTION_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeAction(RuleEngineEvent.AutomationStateChangedEvent event,
                              Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        process(event, channel, tag);
    }

    private void process(RuleEngineEvent.ThresholdStatusChangedEvent event,
                         Channel channel,
                         long tag) throws IOException {
        try {
            notificationFacade.handle(event); channel.basicAck(tag, false);
        } catch (Exception exception) {
            ConsumerFailureLog.error(NOTIFICATION_THRESHOLD_QUEUE, event.eventId(), tag, exception);
            channel.basicNack(tag, false, false);
        }
    }

    private void process(RuleEngineEvent.AutomationStateChangedEvent event,
                         Channel channel,
                         long tag) throws IOException {
        try {
            notificationFacade.handle(event); channel.basicAck(tag, false);
        } catch (Exception exception) {
            ConsumerFailureLog.error(NOTIFICATION_ACTION_QUEUE, event.eventId(), tag, exception);
            channel.basicNack(tag, false, false);
        }
    }
}
