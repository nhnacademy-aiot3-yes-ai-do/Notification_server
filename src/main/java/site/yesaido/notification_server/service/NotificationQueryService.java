package site.yesaido.notification_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.dto.delivery.DeliveryPageResponse;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationDeliveryRepository deliveryRepository;

    public DeliveryPageResponse findDeliveries(Long userId, Pageable pageable) {
        return DeliveryPageResponse.from(deliveryRepository.findPageByUserId(userId, pageable));
    }
}
