package site.yesaido.notification_server.rabbitmq.listener;

import com.rabbitmq.client.Channel;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.rabbitmq.event.CultivationEvent;
import site.yesaido.notification_server.rabbitmq.event.HarvestCompletedPayload;
import site.yesaido.notification_server.rabbitmq.event.MemberAddedPayload;
import site.yesaido.notification_server.rabbitmq.event.NotificationEnvelope;
import site.yesaido.notification_server.rabbitmq.facade.RabbitMqNotificationFacade;
import site.yesaido.notification_server.rabbitmq.ConsumerFailureLog;

import static site.yesaido.notification_server.rabbitmq.RabbitMQConstants.*;

@Component
@RequiredArgsConstructor
public class CultivationRabbitMQConsumer {

    private final RabbitMqNotificationFacade notificationFacade;

    @RabbitListener(queues = NOTIFICATION_HARVEST_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeHarvest(NotificationEnvelope<HarvestCompletedPayload> event, Channel channel,
                               @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try { notificationFacade.handleHarvestCompleted(event); channel.basicAck(tag, false); }
        catch (Exception exception) {
            ConsumerFailureLog.error(NOTIFICATION_HARVEST_QUEUE, eventIdOf(event), tag, exception);
            channel.basicNack(tag, false, false);
        }
    }

    @RabbitListener(queues = NOTIFICATION_SENSOR_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeSensor(CultivationEvent.SensorDataUnavailableEvent event, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try { notificationFacade.handle(event); channel.basicAck(tag, false); }
        catch (Exception exception) { ConsumerFailureLog.error(NOTIFICATION_SENSOR_QUEUE, event.eventId(), tag, exception); channel.basicNack(tag, false, false); }
    }

    @RabbitListener(queues = NOTIFICATION_MEMBER_QUEUE,
            containerFactory = "rabbitListenerContainerFactory")
    public void consumeMember(NotificationEnvelope<MemberAddedPayload> event, Channel channel,
                              @Header(AmqpHeaders.DELIVERY_TAG) long tag) throws IOException {
        try { notificationFacade.handleMemberAdded(event); channel.basicAck(tag, false); }
        catch (Exception exception) {
            ConsumerFailureLog.error(NOTIFICATION_MEMBER_QUEUE, eventIdOf(event), tag, exception);
            channel.basicNack(tag, false, false);
        }
    }

    private static UUID eventIdOf(NotificationEnvelope<?> event) {
        try {
            return event.eventUuid();
        } catch (RuntimeException ignored) {
            return null;
        }
    }
}
