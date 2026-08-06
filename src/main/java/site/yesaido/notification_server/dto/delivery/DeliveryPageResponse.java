package site.yesaido.notification_server.dto.delivery;

import java.util.List;
import org.springframework.data.domain.Page;
import site.yesaido.notification_server.domain.NotificationDelivery;

/** 알림 목록의 페이지 단위 응답이다. Spring Data 내부 타입을 API 밖으로 노출하지 않는다. */
public record DeliveryPageResponse(
        List<DeliveryResponse> content,
        int page,
        int size,
        long totalElements,
        int totalPages,
        boolean hasNext
) {

    public static DeliveryPageResponse from(Page<NotificationDelivery> deliveries) {
        return new DeliveryPageResponse(
                deliveries.map(DeliveryResponse::from).getContent(),
                deliveries.getNumber(),
                deliveries.getSize(),
                deliveries.getTotalElements(),
                deliveries.getTotalPages(),
                deliveries.hasNext());
    }
}
