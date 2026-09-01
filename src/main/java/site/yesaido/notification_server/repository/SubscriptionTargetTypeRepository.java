package site.yesaido.notification_server.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.entity.SubscriptionTargetType;

public interface SubscriptionTargetTypeRepository extends JpaRepository<SubscriptionTargetType, Long> {

    Optional<SubscriptionTargetType> findByTargetType(String targetType);
}
