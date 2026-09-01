package site.yesaido.notification_server.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.entity.NotificationTemplate;

public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    boolean existsByEventType_Id(Long eventTypeId);

    boolean existsByChannelType_Id(Long channelTypeId);

    Optional<NotificationTemplate>
            findFirstByEventType_IdAndChannelType_IdOrderByVersionDesc(
                    Long eventTypeId, Long channelTypeId);

    @Query("""
            select template
            from NotificationTemplate template
            where template.eventType.id = :eventTypeId
              and template.channelType.id in :channelTypeIds
              and template.version = (
                  select max(candidate.version)
                  from NotificationTemplate candidate
                  where candidate.eventType.id = template.eventType.id
                    and candidate.channelType.id = template.channelType.id
              )
            """)
    List<NotificationTemplate> findLatestByEventTypeIdAndChannelTypeIds(
            @Param("eventTypeId") Long eventTypeId,
            @Param("channelTypeIds") Collection<Long> channelTypeIds);
}
