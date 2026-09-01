package site.yesaido.notification_server.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.repository.projection.NotificationEventTypeReferenceProjection;

public interface NotificationEventTypeRepository extends JpaRepository<NotificationEventType, Long> {

    Optional<NotificationEventType> findByCode(String code);

    boolean existsByCodeAndIdNot(String code, Long id);

    @Query(value = """
            SELECT EXISTS (
                       SELECT 1 FROM notification_subscription_type
                       WHERE notification_event_type_id = :eventTypeId
                   ) AS referencedBySubscriptionType,
                   EXISTS (
                       SELECT 1 FROM notification_template
                       WHERE notification_event_type_id = :eventTypeId
                   ) AS referencedByTemplate,
                   EXISTS (
                       SELECT 1 FROM notification
                       WHERE notification_event_type_id = :eventTypeId
                   ) AS referencedByNotification
            """, nativeQuery = true)
    NotificationEventTypeReferenceProjection findReferenceStatus(
            @Param("eventTypeId") Long eventTypeId);
}
