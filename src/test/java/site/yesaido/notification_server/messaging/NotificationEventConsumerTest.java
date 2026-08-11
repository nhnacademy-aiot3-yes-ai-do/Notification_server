package site.yesaido.notification_server.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.AmqpRejectAndDontRequeueException;
import org.springframework.dao.DataIntegrityViolationException;
import site.yesaido.notification_server.exception.messaging.InvalidDomainEventException;
import site.yesaido.notification_server.service.DeliveryDispatchService;
import site.yesaido.notification_server.service.EventProcessingResult;
import site.yesaido.notification_server.service.NotificationEventService;

class NotificationEventConsumerTest {

    private final DomainEventParser parser = mock(DomainEventParser.class);
    private final NotificationEventService eventService = mock(NotificationEventService.class);
    private final DeliveryDispatchService dispatchService = mock(DeliveryDispatchService.class);
    private final NotificationEventConsumer consumer =
            new NotificationEventConsumer(parser, eventService, dispatchService);

    @Test
    void dispatchesDeliveriesCreatedFromEvent() {
        DomainEvent event = event();
        when(parser.parse("message")).thenReturn(event);
        when(eventService.process(event)).thenReturn(
                EventProcessingResult.created(List.of(11L, 12L)));

        consumer.consume("message");

        verify(dispatchService).dispatch(11L);
        verify(dispatchService).dispatch(12L);
    }

    @Test
    void rejectsInvalidEventWithoutRequeue() {
        when(parser.parse("invalid")).thenThrow(
                new InvalidDomainEventException("invalid"));

        assertThatThrownBy(() -> consumer.consume("invalid"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    @Test
    void treatsUniqueConstraintConflictAsDuplicateWhenEventWasPersisted() {
        DomainEvent event = event();
        when(parser.parse("duplicate")).thenReturn(event);
        when(eventService.process(event)).thenThrow(new DataIntegrityViolationException("duplicate"));
        when(eventService.isProcessed(event.eventId())).thenReturn(true);

        consumer.consume("duplicate");

        verify(eventService).isProcessed(event.eventId());
        verify(dispatchService, org.mockito.Mockito.never()).dispatch(org.mockito.ArgumentMatchers.anyLong());
    }

    @Test
    void rejectsConstraintViolationWhenEventWasNotPersisted() {
        DomainEvent event = event();
        when(parser.parse("persistence-failure")).thenReturn(event);
        when(eventService.process(event)).thenThrow(new DataIntegrityViolationException("failure"));
        when(eventService.isProcessed(event.eventId())).thenReturn(false);

        assertThatThrownBy(() -> consumer.consume("persistence-failure"))
                .isInstanceOf(AmqpRejectAndDontRequeueException.class);
    }

    private DomainEvent event() {
        return new DomainEvent(
                UUID.randomUUID(),
                "SENSOR_ERROR",
                "rule-server",
                "CULTIVATION",
                101L,
                LocalDateTime.parse("2026-07-31T10:00:00"),
                new ObjectMapper().createObjectNode());
    }
}
