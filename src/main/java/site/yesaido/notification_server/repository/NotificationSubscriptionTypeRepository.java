package site.yesaido.notification_server.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import site.yesaido.notification_server.entity.NotificationSubscriptionType;

public interface NotificationSubscriptionTypeRepository
        extends JpaRepository<NotificationSubscriptionType, Long> {

    @Query("""
            select st
            from NotificationSubscriptionType st
            join fetch st.eventType
            join fetch st.targetType
            order by st.name
            """)
    List<NotificationSubscriptionType> findAllWithEventAndTargetType();

    boolean existsByEventType_Id(Long eventTypeId);
}
