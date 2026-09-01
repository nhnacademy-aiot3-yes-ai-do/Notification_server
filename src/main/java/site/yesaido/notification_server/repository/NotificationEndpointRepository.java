package site.yesaido.notification_server.repository;

import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import org.springframework.data.jpa.repository.Lock;
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

    boolean existsByChannelType_Id(Long channelTypeId);

    boolean existsByUserIdAndChannelType_IdAndDestinationAndIdNotAndDeletedFalse(
            Long userId, Long channelTypeId, String destination, Long id);

    @Query(value = "SELECT pg_advisory_xact_lock(CAST(:channelTypeId AS integer), hashtext(:destination))", nativeQuery = true)
    void lockActiveDestination(@Param("channelTypeId") Long channelTypeId, @Param("destination") String destination);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<NotificationEndpoint> findAllByChannelType_IdAndDestinationAndDeletedFalse(
            Long channelTypeId, String destination);

    Optional<NotificationEndpoint> findFirstByChannelType_IdAndDestinationAndDeletedFalse(
            Long channelTypeId, String destination);

    Optional<NotificationEndpoint> findFirstByUserIdAndChannelType_IdAndDestinationAndDeletedFalseOrderByIdDesc(
            Long userId, Long channelTypeId, String destination);
}
