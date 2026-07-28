package com.ecosphere.notification.repository;

import com.ecosphere.notification.domain.NotificationSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    List<NotificationSubscription> findAllByEndpoint_UserIdAndDeletedFalse(Long userId);

    Optional<NotificationSubscription> findByIdAndEndpoint_UserIdAndDeletedFalse(Long id, Long userId);

    boolean existsBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndEnabledTrueAndDeletedFalse(
            Long subscriptionTypeId, Long endpointId, Long targetId);
}
