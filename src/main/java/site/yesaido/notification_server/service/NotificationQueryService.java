package site.yesaido.notification_server.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.dto.delivery.DeliveryResponse;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationDeliveryRepository deliveryRepository;

    public List<DeliveryResponse> findDeliveries(Long userId) {
        return deliveryRepository
                .findAllByUserId(userId).stream()
                .map(DeliveryResponse::from)
                .toList();
    }
}
