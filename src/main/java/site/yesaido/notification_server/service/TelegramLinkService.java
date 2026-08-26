package site.yesaido.notification_server.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
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
        LocalDateTime expiresAt = now().plus(properties.expiration());
        linkRepository.create(sessionId, userId, tokenHash, properties.expiration());
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
    CompletionResult completeStart(String token, String chatId) {
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
            NotificationEndpoint activeOwner = endpointRepository
                    .findFirstByChannelType_IdAndDestinationAndDeletedFalse(telegramChannel.getId(), chatId)
                    .orElse(null);
            if (activeOwner != null && !activeOwner.getUserId().equals(pendingLink.userId())) {
                linkRepository.markExpired(pendingLink, properties.expiration());
                return CompletionResult.IGNORED;
            }

            NotificationEndpoint endpoint = activeOwner != null
                    ? activeOwner
                    : new NotificationEndpoint(pendingLink.userId(), telegramChannel, chatId, "Telegram");
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
            if (!linkRepository.markLinked(pendingLink, properties.expiration())) {
                throw new IllegalStateException("Telegram link processing lock was lost before completion");
            }
            sendConfirmation(sender, chatId, pendingLink.userId());
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

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    enum CompletionResult {
        LINKED,
        IGNORED
    }
}
