package site.yesaido.notification_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.entity.SubscriptionTargetType;

import java.util.Optional;

public interface SubscriptionTargetTypeRepository extends JpaRepository<SubscriptionTargetType, Long> {

    Optional<SubscriptionTargetType> findByTargetType(String targetType);
}
