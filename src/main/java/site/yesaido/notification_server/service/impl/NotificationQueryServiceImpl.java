package site.yesaido.notification_server.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.dto.delivery.DeliveryResponse;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;
import site.yesaido.notification_server.service.NotificationQueryService;

@Service
@Transactional(readOnly = true)
public class NotificationQueryServiceImpl implements NotificationQueryService {

    private final NotificationDeliveryRepository deliveryRepository;

    public NotificationQueryServiceImpl(NotificationDeliveryRepository deliveryRepository) {
        this.deliveryRepository = deliveryRepository;
    }

    @Override
    public List<DeliveryResponse> findDeliveries(Long userId) {
        return deliveryRepository
                .findAllBySubscription_Endpoint_UserIdOrderByCreatedAtDesc(userId).stream()
                .map(DeliveryResponse::from)
                .toList();
    }
}
