package site.yesaido.notification_server.repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.notification_server.entity.Notification;
import site.yesaido.notification_server.repository.projection.NotificationEventCountProjection;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    boolean existsBySourceEventId(UUID sourceEventId);

    Optional<Notification> findBySourceEventId(UUID sourceEventId);

    /**
     * RabbitMQ 중복 이벤트를 DB에서 원자적으로 선점한다.
     *
     * JPA의 exists 후 save 방식은 동시 consumer 사이에서 경쟁 조건이 생길 수 있다.
     * 따라서 PostgreSQL의 UNIQUE(source_event_id)와 ON CONFLICT DO NOTHING을
     * 한 문장으로 실행해, 1이면 신규 저장·0이면 이미 처리된 정상 중복으로 구분한다.
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

    /** event_payload에 보존한 원본 이벤트의 재배지와 발생 시각을 기준으로 유형별 집계한다. */
    @Query(value = """
            SELECT event_type.code AS eventTypeCode,
                   event_type.display_name AS eventTypeName,
                   COUNT(notification.id) AS eventCount
            FROM notification
            JOIN notification_event_type event_type
              ON event_type.id = notification.notification_event_type_id
            JOIN subscription_target_type target_type
              ON target_type.id = event_type.target_type
            WHERE target_type.target_type = 'CULTIVATION'
              AND notification.event_payload @> jsonb_build_object('targetId', :cultivationId)
              AND CAST(notification.event_payload ->> 'occurredAt' AS timestamptz) >= :startAt
              AND CAST(notification.event_payload ->> 'occurredAt' AS timestamptz) < :endAt
            GROUP BY event_type.code, event_type.display_name
            ORDER BY event_type.code
            """, nativeQuery = true)
    List<NotificationEventCountProjection> countEventsByCultivationAndOccurredAtBetween(
            @Param("cultivationId") Long cultivationId,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt);

    @Query(value = """
            SELECT CAST(notification.event_payload ->> 'targetId' AS bigint) AS cultivationId,
                   event_type.code AS eventTypeCode,
                   event_type.display_name AS eventTypeName,
                   COUNT(notification.id) AS eventCount
            FROM notification
            JOIN notification_event_type event_type
              ON event_type.id = notification.notification_event_type_id
            JOIN subscription_target_type target_type
              ON target_type.id = event_type.target_type
            WHERE target_type.target_type = 'CULTIVATION'
              AND CAST(notification.event_payload ->> 'targetId' AS bigint) IN (:cultivationIds)
              AND CAST(notification.event_payload ->> 'occurredAt' AS timestamptz) >= :startAt
              AND CAST(notification.event_payload ->> 'occurredAt' AS timestamptz) < :endAt
            GROUP BY CAST(notification.event_payload ->> 'targetId' AS bigint),
                     event_type.code, event_type.display_name
            ORDER BY cultivationId, event_type.code
            """, nativeQuery = true)
    List<NotificationEventCountProjection> countEventsByCultivationsAndOccurredAtBetween(
            @Param("cultivationIds") List<Long> cultivationIds,
            @Param("startAt") OffsetDateTime startAt,
            @Param("endAt") OffsetDateTime endAt);
}
