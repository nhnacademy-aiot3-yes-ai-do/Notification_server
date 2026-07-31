package site.yesaido.notification_server.repository;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.domain.NotificationTemplate;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    Optional<NotificationTemplate>
            findFirstByEventType_IdAndChannelType_IdOrderByVersionDesc(
                    Long eventTypeId, Long channelTypeId);
}
