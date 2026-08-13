package site.yesaido.notification_server.repository;

import site.yesaido.notification_server.entity.NotificationEndpoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationEndpointRepository extends JpaRepository<NotificationEndpoint, Long> {

    @Query("""
            select e
            from NotificationEndpoint e
            join fetch e.channelType c
            where e.userId = :userId
              and e.deleted = false
              and c.deleted = false
            order by e.createdAt desc
            """)
    List<NotificationEndpoint> findAllActiveByUserId(@Param("userId") Long userId);

    Optional<NotificationEndpoint> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    boolean existsByUserIdAndChannelType_IdAndDestinationAndDeletedFalse(
            Long userId, Long channelTypeId, String destination);

    boolean existsByUserIdAndChannelType_IdAndDestinationAndIdNotAndDeletedFalse(
            Long userId, Long channelTypeId, String destination, Long id);
}
