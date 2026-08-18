package site.yesaido.notification_server.rabbitmq.persistence;

import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;

@Service
@RequiredArgsConstructor
public class RabbitMqNotificationDeliveryPersistenceService {

    private final NotificationDeliveryRepository deliveryRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persist(Long notificationId, Long subscriptionId, Long templateId, String renderedMessage) {
        deliveryRepository.upsertCreatedFromRabbitMqFanout(notificationId, subscriptionId, templateId, renderedMessage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void persistFailure(Long notificationId, Long subscriptionId, Long templateId, short attemptCount, String error) {
        deliveryRepository.insertFailedFromRabbitMqFanout(notificationId, subscriptionId, templateId, attemptCount, error);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public List<Long> activateForDispatch(UUID eventId) {
        deliveryRepository.activateCreatedDeliveries(eventId);
        return deliveryRepository.findPendingIdsBySourceEventId(eventId);
    }
}
