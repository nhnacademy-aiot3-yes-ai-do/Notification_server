package site.yesaido.notification_server.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.domain.NotificationEndpoint;
import site.yesaido.notification_server.domain.NotificationSubscription;
import site.yesaido.notification_server.domain.NotificationSubscriptionType;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionResponse;
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;
import site.yesaido.notification_server.exception.EndpointNotFoundException;
import site.yesaido.notification_server.exception.NotificationTargetNotFoundException;
import site.yesaido.notification_server.exception.SubscriptionNotFoundException;
import site.yesaido.notification_server.exception.SubscriptionTypeNotFoundException;
import site.yesaido.notification_server.exception.UnsupportedNotificationChannelException;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionTypeRepository;
import site.yesaido.notification_server.repository.SubscriptionChannelRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationSubscriptionService {

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationSubscriptionTypeRepository subscriptionTypeRepository;
    private final NotificationEndpointRepository endpointRepository;
    private final SubscriptionChannelRepository subscriptionChannelRepository;

    @Transactional
    public SubscriptionResponse create(Long userId, SubscriptionCreateRequest request) {
        NotificationEndpoint endpoint = endpointRepository
                .findByIdAndUserIdAndDeletedFalse(request.endpointId(), userId)
                .orElseThrow(EndpointNotFoundException::new);
        NotificationSubscriptionType type = subscriptionTypeRepository
                .findById(request.subscriptionTypeId())
                .orElseThrow(SubscriptionTypeNotFoundException::new);
        validateUserTarget(userId, request.targetId(), type);

        boolean channelSupported = subscriptionChannelRepository
                .existsBySubscriptionType_IdAndChannelType_Id(
                        type.getId(), endpoint.getChannelType().getId());
        if (!channelSupported) {
            throw new UnsupportedNotificationChannelException(
                    "해당 구독에서 지원하지 않는 알림 채널입니다.");
        }

        return subscriptionRepository
                .findBySubscriptionType_IdAndEndpoint_IdAndTargetIdAndDeletedFalse(
                        type.getId(), endpoint.getId(), request.targetId())
                .map(subscription -> {
                    subscription.changeEnabled(true);
                    return SubscriptionResponse.from(subscription);
                })
                .orElseGet(() -> SubscriptionResponse.from(subscriptionRepository.save(
                        new NotificationSubscription(type, endpoint, request.targetId()))));
    }

    public List<SubscriptionResponse> findAll(Long userId) {
        return subscriptionRepository.findAllActiveByUserId(userId).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    public List<SubscriptionTypeResponse> findTypes() {
        return subscriptionTypeRepository.findAllWithEventAndTargetType().stream()
                .map(SubscriptionTypeResponse::from)
                .toList();
    }

    @Transactional
    public SubscriptionResponse changeEnabled(
            Long userId,
            Long subscriptionId,
            boolean enabled
    ) {
        NotificationSubscription subscription = findOwnedSubscription(userId, subscriptionId);
        subscription.changeEnabled(enabled);
        return SubscriptionResponse.from(subscription);
    }

    @Transactional
    public void delete(Long userId, Long subscriptionId) {
        findOwnedSubscription(userId, subscriptionId).softDelete();
    }

    private NotificationSubscription findOwnedSubscription(Long userId, Long subscriptionId) {
        return subscriptionRepository
                .findByIdAndEndpoint_UserIdAndDeletedFalse(subscriptionId, userId)
                .orElseThrow(SubscriptionNotFoundException::new);
    }

    private void validateUserTarget(
            Long userId,
            Long targetId,
            NotificationSubscriptionType type
    ) {
        /*
         * USER 대상은 Notification이 자체적으로 본인 여부를 검증할 수 있다.
         * CULTIVATION·INQUIRY 대상의 소유권은 해당 서비스의 API 계약이 확정되기 전까지
         * 임의 호출이나 DB 직접 조회를 하지 않는다.
         */
        if ("USER".equals(type.getTargetType().getTargetType()) && !userId.equals(targetId)) {
            throw new NotificationTargetNotFoundException();
        }
    }
}
