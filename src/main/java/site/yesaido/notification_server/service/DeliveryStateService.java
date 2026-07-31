package site.yesaido.notification_server.service;

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
    public DeliveryCommand startAttempt(Long deliveryId) {
        NotificationDelivery delivery = findDelivery(deliveryId);
        delivery.increaseAttemptCount();
        return new DeliveryCommand(
                delivery.getId(),
                delivery.getSubscription().getEndpoint().getChannelType().getCode(),
                delivery.getSubscription().getEndpoint().getDestination(),
                delivery.getRenderedMessage());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markSent(Long deliveryId, String providerMessageId) {
        findDelivery(deliveryId).markSent(providerMessageId);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailed(Long deliveryId, String error) {
        findDelivery(deliveryId).markFailed(sanitize(error));
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
