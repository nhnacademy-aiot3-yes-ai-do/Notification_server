package site.yesaido.notification_server.rabbitmq.refactor.persistence;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.domain.Notification;
import site.yesaido.notification_server.domain.NotificationDelivery;
import site.yesaido.notification_server.domain.NotificationEventType;
import site.yesaido.notification_server.domain.NotificationSubscription;
import site.yesaido.notification_server.domain.NotificationTemplate;
import site.yesaido.notification_server.exception.event.NotificationEventTargetTypeMismatchException;
import site.yesaido.notification_server.exception.event.NotificationEventTypeNotFoundException;
import site.yesaido.notification_server.exception.template.NotificationTemplateNotFoundException;
import site.yesaido.notification_server.rabbitmq.refactor.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.refactor.template.RabbitMqTemplateRenderer;
import site.yesaido.notification_server.repository.NotificationDeliveryRepository;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;

@Service
@RequiredArgsConstructor
public class RabbitMqNotificationPersistenceService {

    private final NotificationRepository notificationRepository;
    private final NotificationEventTypeRepository eventTypeRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationDeliveryRepository deliveryRepository;
    private final RabbitMqTemplateRenderer templateRenderer;
    private final ObjectMapper objectMapper;

    @Transactional
    public void persist(RabbitMqNotificationCommand command) {
        if (notificationRepository.existsBySourceEventId(command.eventId())) {
            return;
        }

        NotificationEventType eventType = eventTypeRepository.findByCode(command.eventCode())
                .orElseThrow(() -> new NotificationEventTypeNotFoundException(command.eventCode()));
        String expectedTargetType = eventType.getTargetType().getTargetType();
        if (!expectedTargetType.equals(command.targetType())) {
            throw new NotificationEventTargetTypeMismatchException(
                    "eventCode=%s, expectedTargetType=%s, actualTargetType=%s".formatted(
                            command.eventCode(), expectedTargetType, command.targetType()));
        }

        Map<String, Object> payload = objectMapper.convertValue(command.payload(), new TypeReference<>() {});
        Notification notification = notificationRepository.save(
                new Notification(command.eventId(), eventType, payload));

        List<NotificationSubscription> subscriptions = subscriptionRepository.findActiveSubscriptions(
                command.eventCode(), command.targetType(), command.targetId());
        for (NotificationSubscription subscription : subscriptions) {
            NotificationTemplate template = templateRepository
                    .findFirstByEventType_IdAndChannelType_IdOrderByVersionDesc(
                            eventType.getId(), subscription.getEndpoint().getChannelType().getId())
                    .orElseThrow(() -> new NotificationTemplateNotFoundException(command.eventCode()));
            String renderedMessage = templateRenderer.render(template.getBodyTemplate(), payload);
            deliveryRepository.save(NotificationDelivery.prepare(
                    notification, subscription, template, renderedMessage));
        }

        // 알림은 비동기 처리, 알림이 동기면 저장도 취소 된다
        // deliveryDispatchService.dispatch(deliveryId);
    }
}
