package site.yesaido.notification_server.repository;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.domain.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsBySourceEventId(UUID sourceEventId);
}
