package site.yesaido.notification_server.service;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import site.yesaido.notification_server.config.NotificationProperties;
import site.yesaido.notification_server.exception.NotificationProviderException;
import site.yesaido.notification_server.messaging.DeadLetterPublisher;
import site.yesaido.notification_server.provider.NotificationSender;
import site.yesaido.notification_server.provider.NotificationSenderRegistry;
import site.yesaido.notification_server.provider.ProviderSendResult;

class DeliveryDispatchServiceTest {

    private final DeliveryStateService stateService = mock(DeliveryStateService.class);
    private final NotificationSenderRegistry registry = mock(NotificationSenderRegistry.class);
    private final DeadLetterPublisher deadLetterPublisher = mock(DeadLetterPublisher.class);
    private final NotificationSender sender = mock(NotificationSender.class);

    @Test
    void retriesProviderFailureAndMarksSuccess() {
        DeliveryDispatchService service = service();
        DeliveryCommand command =
                new DeliveryCommand(7L, "TELEGRAM", "12345", "메시지");
        when(stateService.startAttempt(7L)).thenReturn(command);
        when(registry.get("TELEGRAM")).thenReturn(sender);
        when(sender.send("12345", "메시지"))
                .thenThrow(new NotificationProviderException("temporary"))
                .thenThrow(new NotificationProviderException("temporary"))
                .thenReturn(new ProviderSendResult("99"));

        service.dispatch(7L);

        verify(stateService, times(3)).startAttempt(7L);
        verify(stateService).markSent(7L, "99");
        verify(stateService, never()).markFailed(
                org.mockito.ArgumentMatchers.anyLong(),
                org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void marksFailureAndPublishesDeadLetterAfterRetries() {
        DeliveryDispatchService service = service();
        DeliveryCommand command =
                new DeliveryCommand(7L, "DISCORD", "https://discord.com/api/webhooks/1/t", "메시지");
        when(stateService.startAttempt(7L)).thenReturn(command);
        when(registry.get("DISCORD")).thenReturn(sender);
        when(sender.send(command.destination(), command.message()))
                .thenThrow(new NotificationProviderException("provider down"));

        service.dispatch(7L);

        verify(stateService, times(3)).startAttempt(7L);
        verify(stateService).markFailed(7L, "provider down");
        verify(deadLetterPublisher).publish(7L, "provider down");
    }

    private DeliveryDispatchService service() {
        return new DeliveryDispatchService(
                stateService, registry, deadLetterPublisher, properties());
    }

    private NotificationProperties properties() {
        return new NotificationProperties(
                new NotificationProperties.Rabbit(
                        "events",
                        route(), route(), route(), route(),
                        route(), route(), route(), route(),
                        "dlx", "dlq", "failed"),
                new NotificationProperties.Provider(
                        new NotificationProperties.Telegram("https://api.telegram.org", ""),
                        new NotificationProperties.Discord(List.of("discord.com"))),
                new NotificationProperties.Retry(Duration.ofMillis(1)));
    }

    private NotificationProperties.EventRoute route() {
        return new NotificationProperties.EventRoute("queue", "routing-key");
    }
}
