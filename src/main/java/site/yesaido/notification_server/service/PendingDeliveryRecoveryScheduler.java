package site.yesaido.notification_server.service;

import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import site.yesaido.notification_server.config.property.NotificationRecoveryProperties;
import site.yesaido.notification_server.entity.NotificationDelivery;
import site.yesaido.notification_server.messaging.DeadLetterPublisher;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;

/**
 * Consumer가 DB 저장 후 프로세스 중단으로 끊긴 경우, 오래 남은 PENDING 발송을 다시 처리한다.
 *
 * DB 상태 선점으로 같은 Delivery의 동시 발송은 막는다. 다만 외부 채널 전송 성공 직후
 * SENT 저장 전에 프로세스가 중단되면, at-least-once 특성상 드물게 한 번 중복 발송될 수 있다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PendingDeliveryRecoveryScheduler {

    private final NotificationDeliveryRepository deliveryRepository;
    private final DeliveryDispatchService dispatchService;
    private final DeliveryStateService deliveryStateService;
    private final DeadLetterPublisher deadLetterPublisher;
    private final NotificationRecoveryProperties recoveryProperties;

    @Scheduled(fixedDelayString = "${notification.recovery.pending-delivery-delay:PT1M}")
    public void recoverPendingDeliveries() {
        List<Long> deliveryIds = deliveryRepository.findRecoverablePendingIds(
                LocalDateTime.now().minus(recoveryProperties.pendingDeliveryMinAge()),
                NotificationDelivery.MAX_ATTEMPT_COUNT,
                PageRequest.of(0, recoveryProperties.pendingDeliveryBatchSize()));
        if (!deliveryIds.isEmpty()) {
            log.warn("Recovering stale pending notification deliveries: count={}", deliveryIds.size());
            deliveryIds.forEach(this::dispatchSafely);
        }

        LocalDateTime staleClaimBefore = LocalDateTime.now().minus(recoveryProperties.sendingClaimTimeout());
        List<Long> staleSendingIds = deliveryRepository.findStaleSendingIds(
                staleClaimBefore, PageRequest.of(0, recoveryProperties.pendingDeliveryBatchSize()));
        if (!staleSendingIds.isEmpty()) {
            log.warn("Releasing stale notification delivery claims: count={}", staleSendingIds.size());
            staleSendingIds.forEach(deliveryId ->
                    recoverStaleSendingDeliverySafely(deliveryId, staleClaimBefore));
        }
    }

    private void recoverStaleSendingDeliverySafely(Long deliveryId, LocalDateTime staleClaimBefore) {
        try {
            recoverStaleSendingDelivery(deliveryId, staleClaimBefore);
        } catch (RuntimeException exception) {
            log.error("Stale notification delivery recovery failed but batch will continue: deliveryId={}, "
                            + "failureType={}",
                    deliveryId, exception.getClass().getSimpleName());
        }
    }

    private void recoverStaleSendingDelivery(Long deliveryId, LocalDateTime staleClaimBefore) {
        // attempt_count가 최대치인 상태에서 종료된 건은 재선점할 수 없으므로 FAILED + DLQ로 끝낸다.
        String reason = "최대 발송 시도 후 상태 갱신 전에 처리 프로세스가 종료되었습니다.";
        if (deliveryStateService.failStaleClaimWhenAttemptsExhausted(
                deliveryId, staleClaimBefore, reason)) {
            deadLetterPublisher.publish(deliveryId, reason);
            log.error("Stale notification delivery finalized after exhausted attempts: deliveryId={}",
                    deliveryId);
            return;
        }
        if (deliveryStateService.releaseStaleClaim(deliveryId, staleClaimBefore)) {
            dispatchSafely(deliveryId);
        }
    }

    private void dispatchSafely(Long deliveryId) {
        try {
            dispatchService.dispatch(deliveryId);
        } catch (RuntimeException exception) {
            log.error("Notification recovery dispatch failed but batch will continue: deliveryId={}, failureType={}",
                    deliveryId, exception.getClass().getSimpleName());
        }
    }
}
