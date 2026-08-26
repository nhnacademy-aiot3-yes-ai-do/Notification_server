package site.yesaido.notification_server.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.yesaido.notification_server.config.property.TelegramLinkProperties;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.provider.NotificationSender;
import site.yesaido.notification_server.provider.NotificationSenderRegistry;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;
import site.yesaido.notification_server.repository.TelegramLinkRedisRepository;
import site.yesaido.notification_server.repository.TelegramLinkRedisRepository.PendingLink;
import site.yesaido.notification_server.dto.telegram.TelegramLinkSessionResponse;

class TelegramLinkServiceTest {

    private final TelegramLinkRedisRepository linkRepository = mock(TelegramLinkRedisRepository.class);
    private final ChannelTypeRepository channelTypeRepository = mock(ChannelTypeRepository.class);
    private final NotificationEndpointRepository endpointRepository = mock(NotificationEndpointRepository.class);
    private final NotificationSenderRegistry senderRegistry = mock(NotificationSenderRegistry.class);
    private final NotificationSender telegramSender = mock(NotificationSender.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T02:00:00Z"), ZoneOffset.UTC);
    private final Duration expiration = Duration.ofMinutes(10);

    private TelegramLinkService service;

    @BeforeEach
    void setUp() {
        reset(linkRepository, channelTypeRepository, endpointRepository, senderRegistry, telegramSender);
        when(linkRepository.markLinked(any(), any())).thenReturn(true);
        when(linkRepository.completeLinkedAfterCommit(any(), any()))
                .thenReturn(TelegramLinkRedisRepository.LinkCompletion.TRANSITIONED);
        service = new TelegramLinkService(
                linkRepository,
                channelTypeRepository,
                endpointRepository,
                senderRegistry,
                new TelegramLinkProperties("yes_ai_do_farm_alert_bot", "webhook-secret", expiration),
                clock);
    }

    @Test
    void storesOneTimeLinkOnlyInRedisWithExpiration() {
        TelegramLinkSessionResponse response = service.create(7L);

        assertThat(response.deepLink()).startsWith("https://t.me/yes_ai_do_farm_alert_bot?start=");
        String token = response.deepLink().substring(response.deepLink().indexOf("?start=") + 7);
        assertThat(java.util.Base64.getUrlDecoder().decode(token)).hasSize(32);
        assertThat(response.status()).isEqualTo("PENDING");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-08-25T02:10:00Z"));
        verify(linkRepository).create(eq(response.sessionId()), eq(7L), any(String.class), eq(expiration));
    }

    @Test
    void completesRedisBackedLinkAndSavesTelegramChatAsEndpoint() {
        PendingLink pendingLink = new PendingLink("token-hash", 7L, UUID.randomUUID());
        ChannelType channel = mock(ChannelType.class);
        when(channel.getId()).thenReturn(1L);
        when(linkRepository.acquire(any(), eq(expiration))).thenReturn(Optional.of(pendingLink));
        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM")).thenReturn(Optional.of(channel));
        when(endpointRepository.findAllByChannelType_IdAndDestinationAndDeletedFalse(1L, "123456"))
                .thenReturn(java.util.List.of());
        when(senderRegistry.get("TELEGRAM")).thenReturn(telegramSender);

        TelegramLinkService.CompletionResult result = service.completeStart("opaque-code", "123456");

        assertThat(result).isEqualTo(TelegramLinkService.CompletionResult.LINKED);
        var endpointAccess = inOrder(endpointRepository);
        endpointAccess.verify(endpointRepository).lockActiveDestination(1L, "123456");
        endpointAccess.verify(endpointRepository)
                .findAllByChannelType_IdAndDestinationAndDeletedFalse(1L, "123456");
        verify(endpointRepository).save(any(NotificationEndpoint.class));
        verify(linkRepository).completeLinkedAfterCommit(pendingLink, expiration);
        verify(telegramSender).send("123456", "MushMush Telegram 알림 연동이 완료되었습니다.");
    }

    @Test
    void expiresRedisLinkWhenChatIsAlreadyLinkedToAnotherUser() {
        PendingLink pendingLink = new PendingLink("token-hash", 7L, UUID.randomUUID());
        ChannelType channel = mock(ChannelType.class);
        NotificationEndpoint otherUsersEndpoint = mock(NotificationEndpoint.class);
        when(channel.getId()).thenReturn(1L);
        when(linkRepository.acquire(any(), eq(expiration))).thenReturn(Optional.of(pendingLink));
        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM")).thenReturn(Optional.of(channel));
        when(senderRegistry.get("TELEGRAM")).thenReturn(telegramSender);
        when(endpointRepository.findAllByChannelType_IdAndDestinationAndDeletedFalse(1L, "123456"))
                .thenReturn(java.util.List.of(otherUsersEndpoint));
        when(otherUsersEndpoint.getUserId()).thenReturn(99L);

        TelegramLinkService.CompletionResult result = service.completeStart("opaque-code", "123456");

        assertThat(result).isEqualTo(TelegramLinkService.CompletionResult.IGNORED);
        verify(linkRepository).markExpired(pendingLink, expiration);
    }

    @Test
    void expiresRedisLinkWhenAnyActiveChatOwnerBelongsToAnotherUser() {
        PendingLink pendingLink = new PendingLink("token-hash", 7L, UUID.randomUUID());
        ChannelType channel = mock(ChannelType.class);
        NotificationEndpoint sameUsersEndpoint = mock(NotificationEndpoint.class);
        NotificationEndpoint otherUsersEndpoint = mock(NotificationEndpoint.class);
        when(channel.getId()).thenReturn(1L);
        when(linkRepository.acquire(any(), eq(expiration))).thenReturn(Optional.of(pendingLink));
        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM")).thenReturn(Optional.of(channel));
        when(senderRegistry.get("TELEGRAM")).thenReturn(telegramSender);
        when(endpointRepository.findAllByChannelType_IdAndDestinationAndDeletedFalse(1L, "123456"))
                .thenReturn(java.util.List.of(sameUsersEndpoint, otherUsersEndpoint));
        when(sameUsersEndpoint.getUserId()).thenReturn(7L);
        when(otherUsersEndpoint.getUserId()).thenReturn(99L);

        TelegramLinkService.CompletionResult result = service.completeStart("opaque-code", "123456");

        assertThat(result).isEqualTo(TelegramLinkService.CompletionResult.IGNORED);
        verify(linkRepository).markExpired(pendingLink, expiration);
        verify(endpointRepository, never()).save(any(NotificationEndpoint.class));
    }

    @Test
    void ignoresUnknownRedisLinkWithoutSavingEndpoint() {
        when(linkRepository.acquire(any(), eq(expiration))).thenReturn(Optional.empty());

        TelegramLinkService.CompletionResult result = service.completeStart("unknown", "123456");

        assertThat(result).isEqualTo(TelegramLinkService.CompletionResult.IGNORED);
    }

    @Test
    void releasesRedisLockWhenTransactionRollsBackAfterEndpointSave() {
        PendingLink pendingLink = linkedPendingLink();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.completeStart("opaque-code", "123456");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));

            verify(linkRepository).release(pendingLink);
            verify(linkRepository, never()).markLinked(any(), any());
            verify(telegramSender, never()).send(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void completesRedisStatusAndSendsConfirmationOnlyAfterCommit() {
        PendingLink pendingLink = linkedPendingLink();
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.completeStart("opaque-code", "123456");

            verify(linkRepository, never()).markLinked(any(), any());
            verify(telegramSender, never()).send(any(), any());

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(linkRepository).completeLinkedAfterCommit(pendingLink, expiration);
            verify(telegramSender).send("123456", "MushMush Telegram 알림 연동이 완료되었습니다.");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void retriesTransientRedisCompletionFailureAfterCommitBeforeConfirming() {
        PendingLink pendingLink = linkedPendingLink();
        when(linkRepository.completeLinkedAfterCommit(pendingLink, expiration))
                .thenThrow(new IllegalStateException("redis unavailable"))
                .thenReturn(TelegramLinkRedisRepository.LinkCompletion.ALREADY_LINKED);
        TransactionSynchronizationManager.initSynchronization();
        try {
            service.completeStart("opaque-code", "123456");

            TransactionSynchronizationManager.getSynchronizations()
                    .forEach(TransactionSynchronization::afterCommit);

            verify(linkRepository, org.mockito.Mockito.times(2)).completeLinkedAfterCommit(pendingLink, expiration);
            verify(telegramSender).send("123456", "MushMush Telegram 알림 연동이 완료되었습니다.");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    private PendingLink linkedPendingLink() {
        PendingLink pendingLink = new PendingLink("token-hash", 7L, UUID.randomUUID());
        ChannelType channel = mock(ChannelType.class);
        when(channel.getId()).thenReturn(1L);
        when(linkRepository.acquire(any(), eq(expiration))).thenReturn(Optional.of(pendingLink));
        when(channelTypeRepository.findByCodeAndDeletedFalse("TELEGRAM")).thenReturn(Optional.of(channel));
        when(endpointRepository.findAllByChannelType_IdAndDestinationAndDeletedFalse(1L, "123456"))
                .thenReturn(java.util.List.of());
        when(senderRegistry.get("TELEGRAM")).thenReturn(telegramSender);
        return pendingLink;
    }
}
