package site.yesaido.notification_server.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.domain.Notification;
import site.yesaido.notification_server.domain.NotificationDelivery;
import site.yesaido.notification_server.domain.NotificationEventType;
import site.yesaido.notification_server.domain.NotificationSubscription;
import site.yesaido.notification_server.domain.NotificationTemplate;
import site.yesaido.notification_server.exception.EventContractException;
import site.yesaido.notification_server.exception.NotificationTemplateMissingException;
import site.yesaido.notification_server.messaging.DomainEvent;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;
import site.yesaido.notification_server.template.TemplateRenderer;

@RequiredArgsConstructor
@Service
public class NotificationEventService {

    private final NotificationRepository notificationRepository;
    private final NotificationEventTypeRepository eventTypeRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final TemplateRenderer templateRenderer;
    private final ObjectMapper objectMapper;

    @Transactional
    public EventProcessingResult process(DomainEvent event) {
        if (notificationRepository.existsBySourceEventId(event.eventId())) {
            return EventProcessingResult.duplicateEvent();
        }

        NotificationEventType eventType = eventTypeRepository.findByCode(event.eventType())
                .orElseThrow(() -> new EventContractException(
                        "등록되지 않은 알림 이벤트 유형입니다: " + event.eventType()));
        String expectedTargetType = eventType.getTargetType().getTargetType();
        if (!expectedTargetType.equals(event.targetType())) {
            throw new EventContractException(
                    "이벤트 대상 유형이 기준 정보와 일치하지 않습니다: " + event.targetType());
        }

        List<NotificationSubscription> subscriptions =
                subscriptionRepository.findActiveSubscriptions(
                        event.eventType(), event.targetType(), event.targetId());

        Map<String, Object> payload = objectMapper.convertValue(
                event.payload(), new TypeReference<>() {});
        Notification notification = notificationRepository.save(
                new Notification(event.eventId(), payload));

        List<Long> deliveryIds = new ArrayList<>();
        for (NotificationSubscription subscription : subscriptions) {
            NotificationTemplate template = findTemplate(eventType, subscription);
            String message = templateRenderer.render(template.getBodyTemplate(), event);
            NotificationDelivery delivery = deliveryRepository.save(
                    new NotificationDelivery(notification, subscription, template, message));
            deliveryIds.add(delivery.getId());
        }
        return EventProcessingResult.created(deliveryIds);
    }

    @Transactional(readOnly = true)
    public boolean isProcessed(UUID eventId) {
        return notificationRepository.existsBySourceEventId(eventId);
    }

    private NotificationTemplate findTemplate(
            NotificationEventType eventType,
            NotificationSubscription subscription
    ) {
        return templateRepository
                .findFirstByEventType_IdAndChannelType_IdOrderByVersionDesc(
                        eventType.getId(),
                        subscription.getEndpoint().getChannelType().getId())
                .orElseThrow(() -> new NotificationTemplateMissingException(
                        eventType.getId(), subscription.getEndpoint().getChannelType().getId()));
    }
}
