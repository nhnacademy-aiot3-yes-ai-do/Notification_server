package site.yesaido.notification_server.rabbitmq.listener;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.AiEvent;
import site.yesaido.notification_server.rabbitmq.facade.RabbitMqNotificationFacade;
import site.yesaido.notification_server.rabbitmq.ConsumerFailureLog;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Component
@RequiredArgsConstructor
public class AiRabbitMQConsumer {

    private final RabbitMqNotificationFacade notificationFacade;

    @RabbitListener(queues = NOTIFICATION_DAILY_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeDaily(AiEvent.DailyFeedbackGeneratedEvent event,
                             Channel channel,
                             @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            notificationFacade.handle(event); channel.basicAck(tag, false);
        } catch (Exception exception) {
            ConsumerFailureLog.error(NOTIFICATION_DAILY_QUEUE, event.eventId(), tag, exception);
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = NOTIFICATION_CULTIVATION_COMPLETE_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeCultivationComplete(AiEvent.CultivationCompletedEvent event,
                                           Channel channel,
                                           @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            notificationFacade.handle(event);
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            ConsumerFailureLog.error(NOTIFICATION_CULTIVATION_COMPLETE_QUEUE, event.eventId(), tag, exception);
            channel.basicNack(tag, false, false);
        }
    }
}
