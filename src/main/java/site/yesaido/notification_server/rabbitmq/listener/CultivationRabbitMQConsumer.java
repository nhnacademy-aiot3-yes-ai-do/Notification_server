package site.yesaido.notification_server.rabbitmq.listener;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.facade.RabbitMqNotificationFacade;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Component
@RequiredArgsConstructor
public class CultivationRabbitMQConsumer {

    private final RabbitMqNotificationFacade notificationFacade;

    @RabbitListener(queues = NOTIFICATION_HARVEST_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeHarvest(CultivationEvent.HarvestCompletedEvent event, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try { notificationFacade.handle(event); channel.basicAck(tag, false); }
        catch (Exception exception) { channel.basicNack(tag, false, false); }
    }

    @RabbitListener(queues = NOTIFICATION_SENSOR_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeSensor(CultivationEvent.SensorDataUnavailableEvent event, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try { notificationFacade.handle(event); channel.basicAck(tag, false); }
        catch (Exception exception) { channel.basicNack(tag, false, false); }
    }

    @RabbitListener(queues = NOTIFICATION_MEMBER_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeMember(CultivationEvent.CultivationMemberInvitedEvent event, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try { notificationFacade.handle(event); channel.basicAck(tag, false); }
        catch (Exception exception) { channel.basicNack(tag, false, false); }
    }
}
