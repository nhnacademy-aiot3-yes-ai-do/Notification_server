package site.yesaido.notification_server.repository;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
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
    List<NotificationDelivery> findAllByUserId(@Param("userId") Long userId);
}
