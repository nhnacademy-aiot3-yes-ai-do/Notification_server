package site.yesaido.notification_server.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.domain.NotificationDelivery;
import site.yesaido.notification_server.messaging.DeadLetterPublisher;
import site.yesaido.notification_server.provider.NotificationSender;
import site.yesaido.notification_server.provider.NotificationSenderRegistry;
import site.yesaido.notification_server.provider.ProviderSendResult;

@Service
public class DeliveryDispatchService {

    private static final Logger log = LoggerFactory.getLogger(DeliveryDispatchService.class);

    private final DeliveryStateService stateService;
    private final NotificationSenderRegistry senderRegistry;
    private final DeadLetterPublisher deadLetterPublisher;
    private final RetryTemplate retryTemplate;

    public DeliveryDispatchService(
            DeliveryStateService stateService,
            NotificationSenderRegistry senderRegistry,
            DeadLetterPublisher deadLetterPublisher,
            NotificationProperties properties
    ) {
        this.stateService = stateService;
        this.senderRegistry = senderRegistry;
        this.deadLetterPublisher = deadLetterPublisher;
        this.retryTemplate = RetryTemplate.builder()
                .maxAttempts(NotificationDelivery.MAX_ATTEMPT_COUNT)
                .fixedBackoff(properties.retry().backoff())
                .build();
    }

    public void dispatch(Long deliveryId) {
        retryTemplate.execute(
                context -> {
                    DeliveryCommand command = stateService.startAttempt(deliveryId);
                    NotificationSender sender = senderRegistry.get(command.channelCode());
                    ProviderSendResult result = sender.send(
                            command.destination(), command.message());
                    stateService.markSent(deliveryId, result.messageId());
                    log.info("Notification delivery sent: deliveryId={}, channel={}",
                            deliveryId, command.channelCode());
                    return null;
                },
                context -> {
                    Throwable failure = context.getLastThrowable();
                    String reason = failure == null
                            ? "알 수 없는 발송 오류"
                            : failure.getMessage();
                    stateService.markFailed(deliveryId, reason);
                    deadLetterPublisher.publish(deliveryId, reason);
                    log.error("Notification delivery failed after retries: deliveryId={}",
                            deliveryId, failure);
                    return null;
                });
    }
}
