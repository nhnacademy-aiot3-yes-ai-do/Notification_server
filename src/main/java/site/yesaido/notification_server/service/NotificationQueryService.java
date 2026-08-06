package site.yesaido.notification_server.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.dto.delivery.DeliveryPageResponse;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationQueryService {

    private final NotificationDeliveryRepository deliveryRepository;

    public DeliveryPageResponse findDeliveries(Long userId, int page, int size) {
        return DeliveryPageResponse.from(deliveryRepository.findPageByUserId(
                userId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"))));
    }
}
