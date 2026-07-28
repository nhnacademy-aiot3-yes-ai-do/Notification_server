package com.ecosphere.notification.repository;

import com.ecosphere.notification.domain.NotificationEndpoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEndpointRepository extends JpaRepository<NotificationEndpoint, Long> {

    List<NotificationEndpoint> findAllByUserIdAndDeletedFalse(Long userId);

    Optional<NotificationEndpoint> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);
}
