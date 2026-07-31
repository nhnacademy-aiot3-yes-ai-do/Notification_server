package site.yesaido.notification_server.service.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.domain.NotificationEndpoint;
import site.yesaido.notification_server.domain.NotificationSubscription;
import site.yesaido.notification_server.domain.NotificationSubscriptionType;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionResponse;
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;
import site.yesaido.notification_server.exception.NotificationNotFoundException;
import site.yesaido.notification_server.exception.UnsupportedNotificationChannelException;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionTypeRepository;
import site.yesaido.notification_server.repository.SubscriptionChannelRepository;
import site.yesaido.notification_server.service.NotificationSubscriptionService;

@Service
@Transactional(readOnly = true)
public class NotificationSubscriptionServiceImpl implements NotificationSubscriptionService {

    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationSubscriptionTypeRepository subscriptionTypeRepository;
    private final NotificationEndpointRepository endpointRepository;
    private final SubscriptionChannelRepository subscriptionChannelRepository;

    public NotificationSubscriptionServiceImpl(
            NotificationSubscriptionRepository subscriptionRepository,
            NotificationSubscriptionTypeRepository subscriptionTypeRepository,
            NotificationEndpointRepository endpointRepository,
            SubscriptionChannelRepository subscriptionChannelRepository
    ) {
        this.subscriptionRepository = subscriptionRepository;
        this.subscriptionTypeRepository = subscriptionTypeRepository;
        this.endpointRepository = endpointRepository;
        this.subscriptionChannelRepository = subscriptionChannelRepository;
    }

    @Override
    @Transactional
    public SubscriptionResponse create(Long userId, SubscriptionCreateRequest request) {
        NotificationEndpoint endpoint = endpointRepository
                .findByIdAndUserIdAndDeletedFalse(request.endpointId(), userId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "알림 수신 경로를 찾을 수 없습니다."));
        NotificationSubscriptionType type = subscriptionTypeRepository
                .findById(request.subscriptionTypeId())
                .orElseThrow(() -> new NotificationNotFoundException(
                        "알림 구독 종류를 찾을 수 없습니다."));
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

    @Override
    public List<SubscriptionResponse> findAll(Long userId) {
        return subscriptionRepository.findAllByEndpoint_UserIdAndDeletedFalse(userId).stream()
                .map(SubscriptionResponse::from)
                .toList();
    }

    @Override
    public List<SubscriptionTypeResponse> findTypes() {
        return subscriptionTypeRepository.findAll().stream()
                .map(SubscriptionTypeResponse::from)
                .toList();
    }

    @Override
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

    @Override
    @Transactional
    public void delete(Long userId, Long subscriptionId) {
        findOwnedSubscription(userId, subscriptionId).softDelete();
    }

    private NotificationSubscription findOwnedSubscription(Long userId, Long subscriptionId) {
        return subscriptionRepository
                .findByIdAndEndpoint_UserIdAndDeletedFalse(subscriptionId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "알림 구독을 찾을 수 없습니다."));
    }

    private void validateUserTarget(
            Long userId,
            Long targetId,
            NotificationSubscriptionType type
    ) {
        if ("USER".equals(type.getTargetType().getTargetType()) && !userId.equals(targetId)) {
            throw new NotificationNotFoundException("알림 대상을 찾을 수 없습니다.");
        }
    }
}
