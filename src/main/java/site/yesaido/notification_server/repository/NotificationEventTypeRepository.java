package site.yesaido.notification_server.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.entity.NotificationEventType;

public interface NotificationEventTypeRepository extends JpaRepository<NotificationEventType, Long> {

    Optional<NotificationEventType> findByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);
}
