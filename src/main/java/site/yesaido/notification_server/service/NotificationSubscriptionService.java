package site.yesaido.notification_server.service;

import java.util.List;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionResponse;
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;

public interface NotificationSubscriptionService {

    SubscriptionResponse create(Long userId, SubscriptionCreateRequest request);

    List<SubscriptionResponse> findAll(Long userId);

    List<SubscriptionTypeResponse> findTypes();

    SubscriptionResponse changeEnabled(Long userId, Long subscriptionId, boolean enabled);

    void delete(Long userId, Long subscriptionId);
}
