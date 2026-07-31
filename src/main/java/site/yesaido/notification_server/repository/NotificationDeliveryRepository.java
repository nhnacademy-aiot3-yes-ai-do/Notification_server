package site.yesaido.notification_server.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.domain.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    @EntityGraph(attributePaths = {
        "notification",
        "subscription",
        "subscription.endpoint",
        "subscription.endpoint.channelType",
        "template"
    })
    List<NotificationDelivery> findAllBySubscription_Endpoint_UserIdOrderByCreatedAtDesc(Long userId);
}
