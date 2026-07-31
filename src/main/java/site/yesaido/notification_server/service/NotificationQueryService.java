package site.yesaido.notification_server.service;

import java.util.List;
import site.yesaido.notification_server.dto.delivery.DeliveryResponse;

public interface NotificationQueryService {

    List<DeliveryResponse> findDeliveries(Long userId);
}
