package site.yesaido.notification_server.repository;

import site.yesaido.notification_server.domain.NotificationEndpoint;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationEndpointRepository extends JpaRepository<NotificationEndpoint, Long> {

    List<NotificationEndpoint> findAllByUserIdAndDeletedFalse(Long userId);

    Optional<NotificationEndpoint> findByIdAndUserIdAndDeletedFalse(Long id, Long userId);

    boolean existsByUserIdAndChannelType_IdAndDestinationAndDeletedFalse(
            Long userId, Long channelTypeId, String destination);

    boolean existsByUserIdAndChannelType_IdAndDestinationAndIdNotAndDeletedFalse(
            Long userId, Long channelTypeId, String destination, Long id);
}
