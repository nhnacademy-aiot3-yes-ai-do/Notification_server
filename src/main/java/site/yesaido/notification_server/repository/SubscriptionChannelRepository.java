package site.yesaido.notification_server.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import site.yesaido.notification_server.entity.SubscriptionChannel;

public interface SubscriptionChannelRepository extends JpaRepository<SubscriptionChannel, Long> {

    boolean existsBySubscriptionType_IdAndChannelType_Id(
            Long subscriptionTypeId, Long channelTypeId);
}
