package site.yesaido.notification_server.repository;

import site.yesaido.notification_server.entity.NotificationSubscription;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationSubscriptionRepository extends JpaRepository<NotificationSubscription, Long> {

    @Query("""
            select s
            from NotificationSubscription s
            join fetch s.endpoint e
            join fetch e.channelType c
            join fetch s.subscriptionType st
            join fetch st.eventType
            join fetch st.targetType
            where e.userId = :userId
              and s.deleted = false
              and e.deleted = false
              and c.deleted = false
            order by s.createdAt desc
            """)
    List<NotificationSubscription> findAllActiveByUserId(@Param("userId") Long userId);

    Optional<NotificationSubscription> findByIdAndEndpoint_UserIdAndDeletedFalse(Long id, Long userId);

    List<NotificationSubscription> findAllByEndpoint_IdAndDeletedFalse(Long endpointId);

    Optional<NotificationSubscription> findBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndDeletedFalse(
            Long subscriptionTypeId, Long endpointId, Long targetId);

    boolean existsBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndEnabledTrueAndDeletedFalse(
            Long subscriptionTypeId, Long endpointId, Long targetId);

    @Query("""
            select s
            from NotificationSubscription s
            join fetch s.endpoint e
            join fetch e.channelType c
            join fetch s.subscriptionType st
            join fetch st.eventType et
            join fetch st.targetType tt
            where et.code = :eventType
              and tt.targetType = :targetType
              and s.targetId = :targetId
              and s.enabled = true
              and s.deleted = false
              and e.enabled = true
              and e.deleted = false
              and c.deleted = false
            """)
    List<NotificationSubscription> findActiveSubscriptions(
            @Param("eventType") String eventType,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId);

    @Query("""
            select s
            from NotificationSubscription s
            join fetch s.endpoint e
            join fetch e.channelType c
            join fetch s.subscriptionType st
            join fetch st.eventType et
            join fetch st.targetType tt
            where et.code = :eventType
              and tt.targetType = :targetType
              and s.targetId = :targetId
              and e.userId in :recipientUserIds
              and s.enabled = true
              and s.deleted = false
              and e.enabled = true
              and e.deleted = false
              and c.deleted = false
            """)
    List<NotificationSubscription> findActiveSubscriptionsForRecipientUserIds(
            @Param("eventType") String eventType,
            @Param("targetType") String targetType,
            @Param("targetId") Long targetId,
            @Param("recipientUserIds") List<Long> recipientUserIds);
}
