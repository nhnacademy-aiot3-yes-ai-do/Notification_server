package site.yesaido.notification_server.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.entity.NotificationSubscription;
import site.yesaido.notification_server.entity.NotificationSubscriptionType;
import site.yesaido.notification_server.client.SubscriptionTargetAccessClient;
import site.yesaido.notification_server.dto.subscription.SubscriptionCreateRequest;
import site.yesaido.notification_server.dto.subscription.SubscriptionResponse;
import site.yesaido.notification_server.dto.subscription.SubscriptionTypeResponse;
import site.yesaido.notification_server.exception.subscription.NotificationSubscriptionNotFoundException;
import site.yesaido.notification_server.exception.subscription.SubscriptionCreationEndpointNotFoundException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTargetNotFoundException;
import site.yesaido.notification_server.exception.subscription.SubscriptionTypeNotFoundException;
import site.yesaido.notification_server.exception.subscription.UnsupportedSubscriptionChannelException;
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
    private final SubscriptionTargetAccessClient targetAccessClient;

    @Transactional
    public SubscriptionResponse create(Long userId, SubscriptionCreateRequest request) {
        NotificationEndpoint endpoint = endpointRepository
                .findByIdAndUserIdAndDeletedFalse(request.endpointId(), userId)
                .orElseThrow(() -> new SubscriptionCreationEndpointNotFoundException(
                        "알림 수신 경로를 찾을 수 없습니다."));
        NotificationSubscriptionType type = subscriptionTypeRepository
                .findById(request.subscriptionTypeId())
                .orElseThrow(() -> new SubscriptionTypeNotFoundException(
                        "알림 구독 종류를 찾을 수 없습니다."));
        validateUserTarget(userId, request.targetId(), type);

        boolean channelSupported = subscriptionChannelRepository
                .existsBySubscriptionType_IdAndChannelType_Id(
                        type.getId(), endpoint.getChannelType().getId());
        if (!channelSupported) {
            throw new UnsupportedSubscriptionChannelException(
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
                .orElseThrow(() -> new NotificationSubscriptionNotFoundException(
                        "알림 구독을 찾을 수 없습니다."));
    }

    private void validateUserTarget(
            Long userId,
            Long targetId,
            NotificationSubscriptionType type
    ) {
        String targetType = type.getTargetType().getTargetType();
        if ("USER".equals(targetType)) {
            if (!userId.equals(targetId)) {
                throw new SubscriptionTargetNotFoundException(
                        "user id:%d, target id:%d".formatted(userId, targetId));
            }
            return;
        }
        if ("CULTIVATION".equals(targetType)) {
            targetAccessClient.requireCultivationAccess(userId, targetId);
            return;
        }
        if ("INQUIRY".equals(targetType)) {
            targetAccessClient.requireInquiryAccess(userId, targetId);
        }
    }
}
