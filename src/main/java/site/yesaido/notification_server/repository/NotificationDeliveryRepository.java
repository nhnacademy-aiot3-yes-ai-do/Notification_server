package site.yesaido.notification_server.repository;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import site.yesaido.notification_server.domain.NotificationDelivery;

public interface NotificationDeliveryRepository extends JpaRepository<NotificationDelivery, Long> {

    @Query("""
            select d
            from NotificationDelivery d
            join fetch d.notification
            join fetch d.subscription s
            join fetch s.endpoint e
            join fetch e.channelType
            join fetch d.template
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
}
