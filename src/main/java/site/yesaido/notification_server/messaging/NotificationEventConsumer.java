package site.yesaido.notification_server.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.service.DeliveryDispatchService;
import site.yesaido.notification_server.service.EventProcessingResult;
import site.yesaido.notification_server.service.NotificationEventService;

@Component
public class NotificationEventConsumer {

    private static final Logger log = LoggerFactory.getLogger(NotificationEventConsumer.class);

    private final DomainEventParser eventParser;
    private final NotificationEventService eventService;
    private final DeliveryDispatchService dispatchService;

    public NotificationEventConsumer(
            DomainEventParser eventParser,
            NotificationEventService eventService,
            DeliveryDispatchService dispatchService
    ) {
        this.eventParser = eventParser;
        this.eventService = eventService;
        this.dispatchService = dispatchService;
    }

    @RabbitListener(queues = "${notification.rabbit.queue}")
    public void consume(String message) {
        DomainEvent event = null;
        try {
            event = eventParser.parse(message);
            EventProcessingResult result = eventService.process(event);
            if (result.duplicate()) {
                log.info("Duplicate notification event ignored: eventId={}", event.eventId());
                return;
            }
            log.info("Notification event accepted: eventId={}, eventType={}, deliveries={}",
                    event.eventId(), event.eventType(), result.deliveryIds().size());
            result.deliveryIds().forEach(dispatchService::dispatch);
        } catch (RuntimeException exception) {
            Object eventId = event == null ? "unknown" : event.eventId();
            log.error("Notification event processing failed: eventId={}", eventId, exception);
            throw new AmqpRejectAndDontRequeueException(
                    "Notification event processing failed", exception);
        }
    }
}
