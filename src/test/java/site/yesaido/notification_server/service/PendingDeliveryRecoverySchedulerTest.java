package site.yesaido.notification_server.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import site.yesaido.notification_server.config.property.NotificationRecoveryProperties;
import site.yesaido.notification_server.messaging.DeadLetterPublisher;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;

class PendingDeliveryRecoverySchedulerTest {

    private final NotificationDeliveryRepository deliveryRepository =
            mock(NotificationDeliveryRepository.class);
    private final DeliveryDispatchService dispatchService =
            mock(DeliveryDispatchService.class);
    private final DeliveryStateService deliveryStateService =
            mock(DeliveryStateService.class);
    private final DeadLetterPublisher deadLetterPublisher =
            mock(DeadLetterPublisher.class);
    private final NotificationRecoveryProperties recoveryProperties = new NotificationRecoveryProperties(
            Duration.ofSeconds(30), Duration.ofMinutes(5), 100);

    @Test
    void dispatchesOnlyStalePendingDeliveries() {
        when(deliveryRepository.findRecoverablePendingIds(any(), org.mockito.ArgumentMatchers.anyShort(), any(Pageable.class)))
                .thenReturn(List.of(11L, 12L));
        when(deliveryRepository.findStaleSendingIds(any(), any(Pageable.class))).thenReturn(List.of());
        PendingDeliveryRecoveryScheduler scheduler = scheduler();

        scheduler.recoverPendingDeliveries();

        verify(dispatchService).dispatch(11L);
        verify(dispatchService).dispatch(12L);
    }

    @Test
    void skipsDispatchWhenThereIsNoStalePendingDelivery() {
        when(deliveryRepository.findRecoverablePendingIds(any(), org.mockito.ArgumentMatchers.anyShort(), any(Pageable.class)))
                .thenReturn(List.of());
        when(deliveryRepository.findStaleSendingIds(any(), any(Pageable.class))).thenReturn(List.of());
        PendingDeliveryRecoveryScheduler scheduler = scheduler();

        scheduler.recoverPendingDeliveries();

        verify(dispatchService, never()).dispatch(any());
    }

    @Test
    void continuesRecoveryBatchWhenOneDeliveryDispatchFails() {
        when(deliveryRepository.findRecoverablePendingIds(any(), org.mockito.ArgumentMatchers.anyShort(), any(Pageable.class)))
                .thenReturn(List.of(11L, 12L));
        when(deliveryRepository.findStaleSendingIds(any(), any(Pageable.class))).thenReturn(List.of());
        doThrow(new IllegalStateException("temporary"))
                .when(dispatchService).dispatch(11L);
        PendingDeliveryRecoveryScheduler scheduler = scheduler();

        scheduler.recoverPendingDeliveries();

        verify(dispatchService).dispatch(11L);
        verify(dispatchService).dispatch(12L);
    }

    @Test
    void reclaimsStaleSendingDeliveryBeforeDispatchingIt() {
        when(deliveryRepository.findRecoverablePendingIds(any(), org.mockito.ArgumentMatchers.anyShort(), any(Pageable.class)))
                .thenReturn(List.of());
        when(deliveryRepository.findStaleSendingIds(any(), any(Pageable.class))).thenReturn(List.of(15L));
        when(deliveryStateService.releaseStaleClaim(org.mockito.ArgumentMatchers.eq(15L), any()))
                .thenReturn(true);
        PendingDeliveryRecoveryScheduler scheduler = scheduler();

        scheduler.recoverPendingDeliveries();

        verify(dispatchService).dispatch(15L);
    }

    @Test
    void finalizesExhaustedStaleSendingDeliveryAndPublishesDeadLetter() {
        when(deliveryRepository.findRecoverablePendingIds(any(), org.mockito.ArgumentMatchers.anyShort(), any(Pageable.class)))
                .thenReturn(List.of());
        when(deliveryRepository.findStaleSendingIds(any(), any(Pageable.class))).thenReturn(List.of(16L));
        when(deliveryStateService.failStaleClaimWhenAttemptsExhausted(
                org.mockito.ArgumentMatchers.eq(16L), any(), any()))
                .thenReturn(true);
        PendingDeliveryRecoveryScheduler scheduler = scheduler();

        scheduler.recoverPendingDeliveries();

        verify(deadLetterPublisher).publish(org.mockito.ArgumentMatchers.eq(16L), any());
        verify(dispatchService, never()).dispatch(16L);
        verify(deliveryStateService, never()).releaseStaleClaim(org.mockito.ArgumentMatchers.eq(16L), any());
    }

    @Test
    void continuesStaleSendingRecoveryWhenDeadLetterPublishFails() {
        when(deliveryRepository.findRecoverablePendingIds(any(), org.mockito.ArgumentMatchers.anyShort(), any(Pageable.class)))
                .thenReturn(List.of());
        when(deliveryRepository.findStaleSendingIds(any(), any(Pageable.class))).thenReturn(List.of(16L, 17L));
        when(deliveryStateService.failStaleClaimWhenAttemptsExhausted(
                org.mockito.ArgumentMatchers.eq(16L), any(), any()))
                .thenReturn(true);
        doThrow(new IllegalStateException("dlq unavailable"))
                .when(deadLetterPublisher).publish(org.mockito.ArgumentMatchers.eq(16L), any());
        when(deliveryStateService.releaseStaleClaim(org.mockito.ArgumentMatchers.eq(17L), any()))
                .thenReturn(true);
        PendingDeliveryRecoveryScheduler scheduler = scheduler();

        scheduler.recoverPendingDeliveries();

        verify(dispatchService).dispatch(17L);
    }

    private PendingDeliveryRecoveryScheduler scheduler() {
        return new PendingDeliveryRecoveryScheduler(
                deliveryRepository, dispatchService, deliveryStateService, deadLetterPublisher, recoveryProperties);
    }
}
