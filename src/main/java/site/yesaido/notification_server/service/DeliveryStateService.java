package site.yesaido.notification_server.service;

import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.domain.NotificationDelivery;
import site.yesaido.notification_server.exception.NotificationNotFoundException;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;

@Service
public class DeliveryStateService {

    private final NotificationDeliveryRepository deliveryRepository;

    public DeliveryStateService(NotificationDeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Optional<DeliveryCommand> claimForDispatch(Long deliveryId) {
        /*
         * 발송 선점의 기준은 Entity 조회가 아니라 Repository의 조건부 UPDATE다.
         * Consumer와 복구 스케줄러가 동시에 실행돼도 PENDING -> SENDING 전환에 성공한
         * 한 작업만 외부 Provider를 호출할 수 있다.
         */
        int claimed = deliveryRepository.claimPendingDelivery(
                deliveryId, NotificationDelivery.MAX_ATTEMPT_COUNT);
        if (claimed == 0) {
            return Optional.empty();
        }
        NotificationDelivery delivery = findDelivery(deliveryId);
        return Optional.of(new DeliveryCommand(
                delivery.getId(),
                delivery.getSubscription().getEndpoint().getChannelType().getCode(),
                delivery.getSubscription().getEndpoint().getDestination(),
                delivery.getRenderedMessage(),
                delivery.getAttemptCount()));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordAttempt(Long deliveryId) {
        findDelivery(deliveryId).increaseAttemptCount();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long deliveryId, String providerMessageId) {
        findDelivery(deliveryId).markSent(providerMessageId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long deliveryId, String error) {
        findDelivery(deliveryId).markFailed(sanitize(error));
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean releaseStaleClaim(Long deliveryId, LocalDateTime staleBefore) {
        return deliveryRepository.releaseStaleSendingClaim(deliveryId, staleBefore) == 1;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean failStaleClaimWhenAttemptsExhausted(
            Long deliveryId, LocalDateTime staleBefore, String error
    ) {
        // 시도를 모두 소진한 SENDING은 다시 PENDING으로 풀지 않고 최종 실패로 마감한다.
        return deliveryRepository.failStaleSendingDeliveryWhenAttemptsExhausted(
                deliveryId,
                staleBefore,
                sanitize(error),
                NotificationDelivery.MAX_ATTEMPT_COUNT) == 1;
    }

    private NotificationDelivery findDelivery(Long deliveryId) {
        return deliveryRepository.findById(deliveryId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "발송 이력을 찾을 수 없습니다."));
    }

    private String sanitize(String error) {
        if (error == null || error.isBlank()) {
            return "알 수 없는 발송 오류";
        }
        return error.length() <= 1000 ? error : error.substring(0, 1000);
    }
}
