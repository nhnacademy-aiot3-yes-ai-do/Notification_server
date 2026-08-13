package site.yesaido.notification_server.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.notification_server.entity.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    @Query("""
            select d
            from NotificationDelivery d
            join fetch d.notification
            join fetch d.subscription s
            join fetch s.endpoint e
            join fetch e.channelType
            left join fetch d.template
            where e.userId = :userId
            order by d.createdAt desc
            """)
    Page<NotificationDelivery> findPageByUserId(@Param("userId") Long userId, Pageable pageable);

    @Query("""
            select d.id
            from NotificationDelivery d
            where d.status = 'PENDING'
              and d.attemptCount < :maxAttemptCount
              and d.updatedAt < :staleBefore
            order by d.updatedAt asc
            """)
    List<Long> findRecoverablePendingIds(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("maxAttemptCount") short maxAttemptCount,
            Pageable pageable);

    @Query("""
            select d.id
            from NotificationDelivery d
            where d.status = 'SENDING'
              and d.updatedAt < :staleBefore
            order by d.updatedAt asc
            """)
    List<Long> findStaleSendingIds(@Param("staleBefore") LocalDateTime staleBefore,
                                    Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notification_delivery
            set status = 'SENDING', updated_at = current_timestamp
            where id = :deliveryId
              and status = 'PENDING'
              and attempt_count < :maxAttemptCount
            """, nativeQuery = true)
    int claimPendingDelivery(@Param("deliveryId") Long deliveryId,
                             @Param("maxAttemptCount") short maxAttemptCount);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notification_delivery
            set status = 'PENDING', updated_at = current_timestamp
            where id = :deliveryId
              and status = 'SENDING'
              and updated_at < :staleBefore
            """, nativeQuery = true)
    int releaseStaleSendingClaim(@Param("deliveryId") Long deliveryId,
                                 @Param("staleBefore") LocalDateTime staleBefore);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            update notification_delivery
            set status = 'FAILED', error = :error, updated_at = current_timestamp
            where id = :deliveryId
              and status = 'SENDING'
              and attempt_count >= :maxAttemptCount
              and updated_at < :staleBefore
            """, nativeQuery = true)
    int failStaleSendingDeliveryWhenAttemptsExhausted(
            @Param("deliveryId") Long deliveryId,
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("error") String error,
            @Param("maxAttemptCount") short maxAttemptCount);

    /**
     * RabbitMQ fan-out 정상 성공 저장을 원자적으로 처리한다. 행이 없으면 CREATED로 삽입하고,
     * 기존 행이 FAILED일 때만 같은 행을 CREATED로 복구한다. CREATED/PENDING/SENDING/SENT는 덮어쓰지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into notification_delivery
                (notification_id, notification_subscription_id, notification_template_id,
                 status, rendered_message, attempt_count, error, created_at, updated_at)
            values
                (:notificationId, :subscriptionId, :templateId,
                 'CREATED', :renderedMessage, 0, null, current_timestamp, current_timestamp)
            on conflict (notification_id, notification_subscription_id)
            do update set
                notification_template_id = excluded.notification_template_id,
                rendered_message = excluded.rendered_message,
                status = 'CREATED',
                attempt_count = 0,
                error = null,
                updated_at = current_timestamp
            where notification_delivery.status = 'FAILED'
            """, nativeQuery = true)
    int upsertCreatedFromRabbitMqFanout(
            @Param("notificationId") Long notificationId,
            @Param("subscriptionId") Long subscriptionId,
            @Param("templateId") Long templateId,
            @Param("renderedMessage") String renderedMessage);

    /**
     * RabbitMQ fan-out 최종 실패 저장을 원자적으로 처리한다. 행이 없을 때만 FAILED로 삽입하고,
     * 기존 행은 어떤 상태든 절대 덮어쓰지 않는다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(value = """
            insert into notification_delivery
                (notification_id, notification_subscription_id, notification_template_id,
                 status, rendered_message, attempt_count, error, created_at, updated_at)
            values
                (:notificationId, :subscriptionId, :templateId,
                 'FAILED', '', :attemptCount, :error, current_timestamp, current_timestamp)
            on conflict (notification_id, notification_subscription_id) do nothing
            """, nativeQuery = true)
    int insertFailedFromRabbitMqFanout(
            @Param("notificationId") Long notificationId,
            @Param("subscriptionId") Long subscriptionId,
            @Param("templateId") Long templateId,
            @Param("attemptCount") short attemptCount,
            @Param("error") String error);
}
