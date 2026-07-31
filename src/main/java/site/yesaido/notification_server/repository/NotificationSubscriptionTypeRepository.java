package site.yesaido.notification_server.repository;

import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.domain.NotificationSubscriptionType;

public interface NotificationSubscriptionTypeRepository
        extends JpaRepository<NotificationSubscriptionType, Long> {

    @Override
    @EntityGraph(attributePaths = {"eventType", "targetType"})
    List<NotificationSubscriptionType> findAll();
}
