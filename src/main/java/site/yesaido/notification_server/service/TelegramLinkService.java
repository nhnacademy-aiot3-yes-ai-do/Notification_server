package site.yesaido.notification_server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import site.yesaido.notification_server.config.property.TelegramLinkProperties;
import site.yesaido.notification_server.dto.telegram.TelegramLinkSessionResponse;
import site.yesaido.notification_server.dto.telegram.TelegramLinkStatusResponse;
import site.yesaido.notification_server.entity.ChannelType;
import site.yesaido.notification_server.entity.NotificationEndpoint;
import site.yesaido.notification_server.provider.NotificationSender;
import site.yesaido.notification_server.provider.NotificationSenderRegistry;
import site.yesaido.notification_server.repository.ChannelTypeRepository;
import site.yesaido.notification_server.repository.NotificationEndpointRepository;
import site.yesaido.notification_server.repository.TelegramLinkRedisRepository;
import site.yesaido.notification_server.repository.TelegramLinkRedisRepository.PendingLink;

@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramLinkService {

    private static final int POST_COMMIT_REDIS_ATTEMPTS = 2;
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final String TELEGRAM = "TELEGRAM";
    private static final String LINKED_MESSAGE = "MushMush Telegram 알림 연동이 완료되었습니다.";

    private final TelegramLinkRedisRepository linkRepository;
    private final ChannelTypeRepository channelTypeRepository;
    private final NotificationEndpointRepository endpointRepository;
    private final NotificationSenderRegistry senderRegistry;
    private final TelegramLinkProperties properties;
    private final Clock clock;

    public TelegramLinkSessionResponse create(Long userId) {
        String botUsername = requireBotUsername();
        String token = newToken();
        String tokenHash = hash(token);
        UUID sessionId = TelegramLinkRedisRepository.sessionIdForTokenHash(tokenHash);
        Instant expiresAt = clock.instant().plus(properties.expiration());
        linkRepository.create(sessionId, userId, properties.expiration());
        return new TelegramLinkSessionResponse(
                sessionId,
                TelegramLinkRedisRepository.LinkStatus.PENDING.name(),
                "https://t.me/%s?start=%s".formatted(botUsername, token),
                expiresAt);
    }

    public TelegramLinkStatusResponse status(Long userId, UUID sessionId) {
        return new TelegramLinkStatusResponse(sessionId, linkRepository.status(userId, sessionId).name());
    }

    @Transactional
    public CompletionResult completeStart(String token, String chatId) {
        PendingLink pendingLink = linkRepository.acquire(hash(token), properties.expiration()).orElse(null);
        if (pendingLink == null) {
            return CompletionResult.IGNORED;
        }

        try {
            ChannelType telegramChannel = channelTypeRepository.findByCodeAndDeletedFalse(TELEGRAM)
                    .orElseThrow(() -> new IllegalStateException("Telegram 채널 기준정보가 없습니다."));
            NotificationSender sender = senderRegistry.get(TELEGRAM);
            sender.validateDestination(chatId);

            endpointRepository.lockActiveDestination(telegramChannel.getId(), chatId);
            java.util.List<NotificationEndpoint> activeOwners = endpointRepository
                    .findAllByChannelType_IdAndDestinationAndDeletedFalse(telegramChannel.getId(), chatId);
            if (activeOwners.stream().anyMatch(owner -> !owner.getUserId().equals(pendingLink.userId()))) {
                linkRepository.markExpired(pendingLink, properties.expiration());
                return CompletionResult.IGNORED;
            }

            NotificationEndpoint endpoint = activeOwners.stream().findFirst()
                    .orElseGet(() -> new NotificationEndpoint(
                            pendingLink.userId(), telegramChannel, chatId, "Telegram"));
            endpoint.changeEnabled(true);
            endpointRepository.save(endpoint);
            completeAfterCommit(sender, chatId, pendingLink);
            return CompletionResult.LINKED;
        } catch (RuntimeException exception) {
            linkRepository.release(pendingLink);
            throw exception;
        }
    }

    private void completeAfterCommit(NotificationSender sender, String chatId, PendingLink pendingLink) {
        Runnable complete = () -> {
            CompletionAttempt completionAttempt = completeRedisStatus(pendingLink);
            TelegramLinkRedisRepository.LinkCompletion completion = completionAttempt.completion();
            if (completion == TelegramLinkRedisRepository.LinkCompletion.TRANSITIONED
                    || (completion == TelegramLinkRedisRepository.LinkCompletion.ALREADY_LINKED
                    && completionAttempt.recoveredAfterFailure())) {
                sendConfirmation(sender, chatId, pendingLink.userId());
                return;
            }
            if (completion != TelegramLinkRedisRepository.LinkCompletion.ALREADY_LINKED) {
                throw new IllegalStateException("Telegram link status could not be completed after database commit");
            }
        };
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    complete.run();
                }

                @Override
                public void afterCompletion(int status) {
                    if (status != STATUS_COMMITTED) {
                        linkRepository.release(pendingLink);
                    }
                }
            });
            return;
        }
        complete.run();
    }

    private CompletionAttempt completeRedisStatus(PendingLink pendingLink) {
        RuntimeException lastFailure = null;
        for (int attempt = 1; attempt <= POST_COMMIT_REDIS_ATTEMPTS; attempt++) {
            try {
                return new CompletionAttempt(
                        linkRepository.completeLinkedAfterCommit(pendingLink, properties.expiration()),
                        lastFailure != null);
            } catch (RuntimeException exception) {
                lastFailure = exception;
                log.warn("Telegram link Redis completion attempt failed: attempt={}, failureType={}",
                        attempt, exception.getClass().getSimpleName());
            }
        }
        throw new IllegalStateException("Telegram link Redis completion failed after database commit", lastFailure);
    }

    private record CompletionAttempt(
            TelegramLinkRedisRepository.LinkCompletion completion,
            boolean recoveredAfterFailure) {
    }

    private void sendConfirmation(NotificationSender sender, String chatId, Long userId) {
        try {
            sender.send(chatId, LINKED_MESSAGE);
        } catch (RuntimeException exception) {
            log.warn("Telegram link confirmation delivery failed: userId={}, failureType={}",
                    userId, exception.getClass().getSimpleName());
        }
    }

    private String requireBotUsername() {
        String botUsername = properties.botUsername();
        if (botUsername == null || botUsername.isBlank()) {
            throw new IllegalStateException("Telegram Bot username이 설정되지 않았습니다.");
        }
        return botUsername;
    }

    private String newToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String value) {
        try {
            return java.util.HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256을 사용할 수 없습니다.", exception);
        }
    }

    enum CompletionResult {
        LINKED,
        IGNORED
    }
}
