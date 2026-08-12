package site.yesaido.notification_server.rabbitmq;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Component
public class CultivationRabbitMQConsumer {

    @RabbitListener(queues = NOTIFICATION_HARVEST_QUEUE, autoStartup = "false")
    public void consumeHarvest(CultivationEvent.HarvestCompletedEvent event,
                               Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = NOTIFICATION_SENSOR_QUEUE, autoStartup = "false")
    public void consumeSensor(CultivationEvent.SensorDataUnavailableEvent event,
                              Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = NOTIFICATION_MEMBER_QUEUE, autoStartup = "false")
    public void consumeMember(CultivationEvent.CultivationMemberInvitedEvent event,
                              Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try {
            channel.basicAck(tag, false);
        } catch (Exception exception) {
            channel.basicNack(tag, false, false);
        }
    }
}
