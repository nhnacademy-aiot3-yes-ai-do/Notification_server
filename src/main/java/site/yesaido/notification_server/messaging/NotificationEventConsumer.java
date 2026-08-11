package site.yesaido.notification_server.messaging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.config.NotificationRabbitConstants;
import site.yesaido.notification_server.exception.messaging.InvalidDomainEventException;
import site.yesaido.notification_server.exception.event.NotificationEventTargetTypeMismatchException;
import site.yesaido.notification_server.exception.event.NotificationEventTypeNotFoundException;
import site.yesaido.notification_server.exception.template.NotificationTemplateNotFoundException;
import site.yesaido.notification_server.exception.template.NotificationTemplateVariableMissingException;
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

    @RabbitListener(queues = {
            NotificationRabbitConstants.THRESHOLD_QUEUE,
            NotificationRabbitConstants.ACTION_QUEUE,
            NotificationRabbitConstants.DAILY_QUEUE,
            NotificationRabbitConstants.LOGIN_QUEUE,
            NotificationRabbitConstants.QUESTION_QUEUE,
            NotificationRabbitConstants.ANSWER_QUEUE,
            NotificationRabbitConstants.HARVEST_QUEUE,
            NotificationRabbitConstants.CULTIVATION_FINISHED_QUEUE
    })
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
        } catch (DataIntegrityViolationException exception) {
            if (event != null && eventService.isProcessed(event.eventId())) {
                log.info("Duplicate notification event ignored after unique constraint: {}",
                        eventMetadata(event));
                return;
            }
            log.error("Notification event persistence failed: {}", eventMetadata(event), exception);
            throw reject(exception);
        } catch (InvalidDomainEventException
                 | NotificationEventTypeNotFoundException
                 | NotificationEventTargetTypeMismatchException exception) {
            log.warn("Notification event contract rejected: {}", eventMetadata(event), exception);
            throw reject(exception);
        } catch (NotificationTemplateVariableMissingException
                 | NotificationTemplateNotFoundException exception) {
            log.error("Notification event configuration failed: {}", eventMetadata(event), exception);
            throw reject(exception);
        } catch (RuntimeException exception) {
            log.error("Notification event system processing failed: {}", eventMetadata(event), exception);
            throw reject(exception);
        }
    }

    private AmqpRejectAndDontRequeueException reject(RuntimeException exception) {
        return new AmqpRejectAndDontRequeueException(
                "Notification event processing failed", exception);
    }

    private String eventMetadata(DomainEvent event) {
        if (event == null) {
            return "eventId=unknown";
        }
        return "eventId=" + event.eventId()
                + ", eventType=" + event.eventType()
                + ", targetType=" + event.targetType()
                + ", targetId=" + event.targetId();
    }
}
