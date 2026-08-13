package site.yesaido.notification_server.rabbitmq.persistence;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.entity.NotificationDelivery;
import site.yesaido.notification_server.entity.NotificationEventType;
import site.yesaido.notification_server.entity.NotificationSubscription;
import site.yesaido.notification_server.entity.NotificationTemplate;
import site.yesaido.notification_server.exception.event.NotificationEventTargetTypeMismatchException;
import site.yesaido.notification_server.exception.event.NotificationEventTypeNotFoundException;
import site.yesaido.notification_server.rabbitmq.command.RabbitMqNotificationCommand;
import site.yesaido.notification_server.rabbitmq.persistence.RabbitMqNotificationCreationService.RabbitMqNotificationCreationResult;
import site.yesaido.notification_server.rabbitmq.template.RabbitMqTemplateRenderer;
import site.yesaido.notification_server.repository.NotificationEventTypeRepository;
import site.yesaido.notification_server.repository.NotificationSubscriptionRepository;
import site.yesaido.notification_server.repository.NotificationTemplateRepository;

@Slf4j
@Service
public class RabbitMqNotificationPersistenceService {

    private final NotificationEventTypeRepository eventTypeRepository;
    private final NotificationSubscriptionRepository subscriptionRepository;
    private final NotificationTemplateRepository templateRepository;
    private final RabbitMqNotificationCreationService notificationCreationService;
    private final RabbitMqNotificationDeliveryPersistenceService deliveryPersistenceService;
    private final RabbitMqTemplateRenderer templateRenderer;
    private final Duration retryBackoff;

    @Autowired
    public RabbitMqNotificationPersistenceService(
            NotificationEventTypeRepository eventTypeRepository,
            NotificationSubscriptionRepository subscriptionRepository,
            NotificationTemplateRepository templateRepository,
            RabbitMqNotificationCreationService notificationCreationService,
            RabbitMqNotificationDeliveryPersistenceService deliveryPersistenceService,
            RabbitMqTemplateRenderer templateRenderer,
            NotificationProperties properties) {
        this.eventTypeRepository = eventTypeRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.templateRepository = templateRepository;
        this.notificationCreationService = notificationCreationService;
        this.deliveryPersistenceService = deliveryPersistenceService;
        this.templateRenderer = templateRenderer;
        this.retryBackoff = properties.retry().backoff();
    }

    @Transactional(readOnly = true)
    public RabbitMqPersistenceResult persist(RabbitMqNotificationCommand command) {
        NotificationEventType eventType = eventTypeRepository.findByCode(command.eventCode())
                .orElseThrow(() -> new NotificationEventTypeNotFoundException(command.eventCode()));
        String expectedTargetType = eventType.getTargetType().getTargetType();
        if (!expectedTargetType.equals(command.targetType())) {
            throw new NotificationEventTargetTypeMismatchException(
                    "eventCode=%s, expectedTargetType=%s, actualTargetType=%s".formatted(
                            command.eventCode(), expectedTargetType, command.targetType()));
        }

        Map<String, Object> payload = payload(command);
        RabbitMqNotificationCreationResult creation =
                notificationCreationService.createIfAbsent(command.eventId(), eventType, payload);
        List<NotificationSubscription> subscriptions = subscriptionRepository.findActiveSubscriptions(
                command.eventCode(), command.targetType(), command.targetId());
        Map<Long, NotificationTemplate> templatesByChannelTypeId = templatesByChannelTypeId(eventType.getId(), subscriptions);
        List<DeliveryFailure> failures = new ArrayList<>();
        for (NotificationSubscription subscription : subscriptions) {
            attemptDelivery(command, creation.notificationId(), subscription, templatesByChannelTypeId, payload)
                    .ifPresent(failures::add);
        }
        retryFailures(command, creation.notificationId(), failures, payload);
        return creation.created() ? RabbitMqPersistenceResult.PERSISTED : RabbitMqPersistenceResult.ALREADY_PROCESSED;
    }

    private Map<Long, NotificationTemplate> templatesByChannelTypeId(
            Long eventTypeId, List<NotificationSubscription> subscriptions) {
        List<Long> channelTypeIds = subscriptions.stream()
                .map(subscription -> subscription.getEndpoint().getChannelType().getId())
                .distinct()
                .toList();
        return templateRepository.findLatestByEventTypeIdAndChannelTypeIds(eventTypeId, channelTypeIds).stream()
                .collect(Collectors.toMap(template -> template.getChannelType().getId(), Function.identity()));
    }

    private java.util.Optional<DeliveryFailure> attemptDelivery(
            RabbitMqNotificationCommand command,
            Long notificationId,
            NotificationSubscription subscription,
            Map<Long, NotificationTemplate> templatesByChannelTypeId,
            Map<String, Object> payload) {
        Long channelTypeId = subscription.getEndpoint().getChannelType().getId();
        NotificationTemplate template = templatesByChannelTypeId.get(channelTypeId);
        if (template == null) {
            return java.util.Optional.of(new DeliveryFailure(subscription, null,
                    new IllegalStateException("template not found for channelTypeId=" + channelTypeId)));
        }
        String renderedMessage;
        try {
            renderedMessage = templateRenderer.render(template.getBodyTemplate(), payload);
        } catch (RuntimeException exception) {
            log.warn("RabbitMQ notification delivery attempt failed: eventId={}, eventCode={}, subscriptionId={}, channelTypeId={}, failureType={}",
                    command.eventId(), command.eventCode(), subscription.getId(), channelTypeId,
                    exception.getClass().getSimpleName());
            return java.util.Optional.of(new DeliveryFailure(subscription, template, exception));
        }
        deliveryPersistenceService.persist(notificationId, subscription.getId(), template.getId(), renderedMessage);
        return java.util.Optional.empty();
    }

    private void retryFailures(
            RabbitMqNotificationCommand command, Long notificationId, List<DeliveryFailure> failures,
            Map<String, Object> payload) {
        for (DeliveryFailure failure : failures) {
            RetryTemplate retryTemplate = RetryTemplate.builder()
                    .maxAttempts(NotificationDelivery.MAX_ATTEMPT_COUNT - 1)
                    .fixedBackoff(retryBackoff)
                    .build();
            String renderedMessage = retryTemplate.execute(
                    context -> {
                        NotificationTemplate template = failure.template();
                        if (template == null) {
                            throw failure.cause();
                        }
                        return templateRenderer.render(template.getBodyTemplate(), payload);
                    },
                    context -> {
                        Throwable cause = context.getLastThrowable();
                        Long channelTypeId = failure.subscription().getEndpoint().getChannelType().getId();
                        String reason = failureReason(cause);
                        try {
                            deliveryPersistenceService.persistFailure(
                                    notificationId,
                                    failure.subscription().getId(),
                                    failure.template() == null ? null : failure.template().getId(),
                                    (short) (1 + context.getRetryCount()),
                                    reason);
                        } catch (RuntimeException persistenceFailure) {
                            RabbitMqNotificationFailureHistoryPersistenceException escalation =
                                    new RabbitMqNotificationFailureHistoryPersistenceException(
                                            "failed to persist FAILED delivery history: eventId=%s, eventCode=%s, subscriptionId=%s, channelTypeId=%s, originalFailureType=%s, originalFailureReason=%s"
                                                    .formatted(command.eventId(), command.eventCode(),
                                                            failure.subscription().getId(), channelTypeId,
                                                            cause == null ? "Unknown" : cause.getClass().getSimpleName(), reason),
                                            persistenceFailure);
                            if (cause != null) {
                                escalation.addSuppressed(cause);
                            }
                            log.error("RABBITMQ_NOTIFICATION_FAILURE_HISTORY_PERSISTENCE_FAILED eventId={}, eventCode={}, subscriptionId={}, channelTypeId={}, originalFailureType={}, originalFailureReason={}, persistenceFailureType={}",
                                    command.eventId(), command.eventCode(), failure.subscription().getId(), channelTypeId,
                                    cause == null ? "Unknown" : cause.getClass().getSimpleName(), reason,
                                    persistenceFailure.getClass().getSimpleName(), escalation);
                            throw escalation;
                        }
                        log.error("RABBITMQ_NOTIFICATION_DELIVERY_FINAL_FAILURE eventId={}, eventCode={}, subscriptionId={}, channelTypeId={}, attempts={}, failureType={}, reason={}",
                                command.eventId(), command.eventCode(), failure.subscription().getId(), channelTypeId,
                                NotificationDelivery.MAX_ATTEMPT_COUNT,
                                cause == null ? "Unknown" : cause.getClass().getSimpleName(), reason);
                        return null;
                    });
            if (renderedMessage != null) {
                NotificationTemplate template = failure.template();
                deliveryPersistenceService.persist(
                        notificationId, failure.subscription().getId(), template.getId(), renderedMessage);
            }
        }
    }

    private String failureReason(Throwable cause) {
        String message = cause == null || cause.getMessage() == null || cause.getMessage().isBlank()
                ? "unknown" : cause.getMessage();
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }

    private record DeliveryFailure(
            NotificationSubscription subscription, NotificationTemplate template, RuntimeException cause) {
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> payload(RabbitMqNotificationCommand command) {
        if (!(command.payload() instanceof Map<?, ?>)) {
            throw new IllegalArgumentException("RabbitMQ notification payload must be an explicit template variable map");
        }
        return (Map<String, Object>) command.payload();
    }
}
