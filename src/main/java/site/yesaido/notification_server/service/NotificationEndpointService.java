package site.yesaido.notification_server.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.domain.ChannelType;
import site.yesaido.notification_server.domain.NotificationEndpoint;
import site.yesaido.notification_server.dto.endpoint.EndpointCreateRequest;
import site.yesaido.notification_server.dto.endpoint.EndpointResponse;
import site.yesaido.notification_server.dto.endpoint.EndpointUpdateRequest;
import site.yesaido.notification_server.exception.DuplicateNotificationResourceException;
import site.yesaido.notification_server.exception.NotificationNotFoundException;
import site.yesaido.notification_server.provider.NotificationSenderRegistry;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationEndpointService {

    private final NotificationEndpointRepository endpointRepository;
    private final ChannelTypeRepository channelTypeRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationSenderRegistry senderRegistry;

    @Transactional
    public EndpointResponse create(Long userId, EndpointCreateRequest request) {
        ChannelType channel = channelTypeRepository.findByIdAndDeletedFalse(request.channelTypeId())
                .orElseThrow(() -> new NotificationNotFoundException("알림 채널을 찾을 수 없습니다."));
        senderRegistry.get(channel.getCode()).validateDestination(request.destination());

        boolean duplicate = endpointRepository
                .existsByUserIdAndChannelType_IdAndDestinationAndDeletedFalse(
                        userId, channel.getId(), request.destination());
        if (duplicate) {
            throw new DuplicateNotificationResourceException("이미 등록된 알림 수신 경로입니다.");
        }

        NotificationEndpoint endpoint = new NotificationEndpoint(
                userId, channel, request.destination(), request.displayName());
        return EndpointResponse.from(endpointRepository.save(endpoint));
    }

    public List<EndpointResponse> findAll(Long userId) {
        return endpointRepository.findAllActiveByUserId(userId).stream()
                .map(EndpointResponse::from)
                .toList();
    }

    @Transactional
    public EndpointResponse update(
            Long userId,
            Long endpointId,
            EndpointUpdateRequest request
    ) {
        NotificationEndpoint endpoint = findOwnedEndpoint(userId, endpointId);
        senderRegistry.get(endpoint.getChannelType().getCode())
                .validateDestination(request.destination());
        boolean duplicate = endpointRepository
                .existsByUserIdAndChannelType_IdAndDestinationAndIdNotAndDeletedFalse(
                        userId,
                        endpoint.getChannelType().getId(),
                        request.destination(),
                        endpointId);
        if (duplicate) {
            throw new DuplicateNotificationResourceException("이미 등록된 알림 수신 경로입니다.");
        }
        endpoint.update(request.destination(), request.displayName());
        return EndpointResponse.from(endpoint);
    }

    @Transactional
    public EndpointResponse changeEnabled(Long userId, Long endpointId, boolean enabled) {
        NotificationEndpoint endpoint = findOwnedEndpoint(userId, endpointId);
        endpoint.changeEnabled(enabled);
        return EndpointResponse.from(endpoint);
    }

    @Transactional
    public void delete(Long userId, Long endpointId) {
        NotificationEndpoint endpoint = findOwnedEndpoint(userId, endpointId);
        endpoint.softDelete();
        subscriptionRepository.findAllByEndpoint_IdAndDeletedFalse(endpointId)
                .forEach(subscription -> subscription.softDelete());
    }

    private NotificationEndpoint findOwnedEndpoint(Long userId, Long endpointId) {
        return endpointRepository.findByIdAndUserIdAndDeletedFalse(endpointId, userId)
                .orElseThrow(() -> new NotificationNotFoundException(
                        "알림 수신 경로를 찾을 수 없습니다."));
    }
}
