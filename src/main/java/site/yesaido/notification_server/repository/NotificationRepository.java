package site.yesaido.notification_server.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.notification_server.entity.Notification;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsBySourceEventId(UUID sourceEventId);

    Optional<Notification> findBySourceEventId(UUID sourceEventId);

    /**
     * UNIQUE(source_event_id)를 DB에서 원자적으로 선점한다. 0이면 다른 consumer가 먼저 저장한 정상 중복이다.
     */
    @Modifying
    @Query(value = """
            INSERT INTO notification (source_event_id, notification_event_type_id, event_payload)
            VALUES (:sourceEventId, :eventTypeId, CAST(:payload AS jsonb))
            ON CONFLICT (source_event_id) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(@Param("sourceEventId") UUID sourceEventId,
                       @Param("eventTypeId") Long eventTypeId,
                       @Param("payload") String payload);
}
