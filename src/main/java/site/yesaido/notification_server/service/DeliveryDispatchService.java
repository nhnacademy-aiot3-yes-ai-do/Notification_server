package site.yesaido.notification_server.service;

import java.time.Duration;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.stereotype.Service;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.entity.NotificationDelivery;
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
    private final Duration retryBackoff;

    public DeliveryDispatchService(
            DeliveryStateService stateService,
            NotificationSenderRegistry senderRegistry,
            DeadLetterPublisher deadLetterPublisher,
            NotificationProperties properties
    ) {
        this.stateService = stateService;
        this.senderRegistry = senderRegistry;
        this.deadLetterPublisher = deadLetterPublisher;
        this.retryBackoff = properties.retry().backoff();
    }

    public void dispatch(Long deliveryId) {
        Optional<DeliveryCommand> claimedCommand = stateService.claimForDispatch(deliveryId);
        if (claimedCommand.isEmpty()) {
            log.debug("Notification delivery dispatch skipped because it was already claimed or finalized: deliveryId={}",
                    deliveryId);
            return;
        }

        DeliveryCommand command = claimedCommand.get();
        short remainingAttempts = command.remainingAttempts(NotificationDelivery.MAX_ATTEMPT_COUNT);
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(remainingAttempts)
                .fixedBackoff(retryBackoff)
                .build();

        retryTemplate.execute(
                context -> {
                    stateService.recordAttempt(deliveryId);
                    NotificationSender sender = senderRegistry.get(command.channelCode());
                    int attempt = command.attemptCount() + context.getRetryCount() + 1;
                    try {
                        ProviderSendResult result = sender.send(
                                command.destination(), command.message());
                        stateService.markSent(deliveryId, result.messageId());
                        log.info("Notification delivery sent: deliveryId={}, channel={}, attempt={}",
                                deliveryId, command.channelCode(), attempt);
                        return null;
                    } catch (RuntimeException exception) {
                        log.warn("Notification delivery attempt failed: deliveryId={}, channel={}, "
                                        + "attempt={}/{}, failureType={}",
                                deliveryId,
                                command.channelCode(),
                                attempt,
                                NotificationDelivery.MAX_ATTEMPT_COUNT,
                                exception.getClass().getSimpleName());
                        throw exception;
                    }
                },
                context -> {
                    Throwable failure = context.getLastThrowable();
                    String reason = failure == null
                            ? "알 수 없는 발송 오류"
                            : failure.getMessage();
                    stateService.markFailed(deliveryId, reason);
                    deadLetterPublisher.publish(deliveryId, reason);
                    log.error("Notification delivery failed after retries: deliveryId={}, attempts={}, "
                                    + "failureType={}",
                            deliveryId,
                            command.attemptCount() + remainingAttempts,
                            failure == null ? "Unknown" : failure.getClass().getSimpleName());
                    return null;
                });
    }
}
