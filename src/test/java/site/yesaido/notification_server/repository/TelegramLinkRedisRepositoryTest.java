package site.yesaido.notification_server.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Optional;
import java.util.UUID;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

class TelegramLinkRedisRepositoryTest {

    private final StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
    @SuppressWarnings("unchecked")
    private final ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
    private final Duration expiration = Duration.ofMinutes(10);
    private TelegramLinkRedisRepository repository;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        repository = new TelegramLinkRedisRepository(redisTemplate);
    }

    @Test
    void createsClusterCompatibleExpiringRedisKeys() {
        UUID sessionId = UUID.fromString("0d9cce63-4fcf-4c45-aa8a-f6a0adcf7d79");
        String tokenHash = "hash";

        repository.create(sessionId, 7L, tokenHash, expiration);

        verify(redisTemplate).execute(
                any(),
                eq(java.util.List.of(
                        "notification:telegram-link:status:{" + sessionId + "}:status",
                        "notification:telegram-link:token:{" + sessionId + "}:token")),
                eq("7:" + sessionId + ":PENDING"),
                eq("7:" + sessionId),
                eq("600"));
    }

    @Test
    void acquiresOneTimeTokenWithShortRedisMutexTtl() {
        UUID sessionId = TelegramLinkRedisRepository.sessionIdForTokenHash("hash");
        when(valueOperations.setIfAbsent(
                eq("notification:telegram-link:lock:{" + sessionId + "}:lock"), any(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(valueOperations.get("notification:telegram-link:token:{" + sessionId + "}:token"))
                .thenReturn("7:" + sessionId);

        Optional<TelegramLinkRedisRepository.PendingLink> result = repository.acquire("hash", expiration);

        assertThat(result).hasValueSatisfying(link -> {
            assertThat(link.tokenHash()).isEqualTo("hash");
            assertThat(link.userId()).isEqualTo(7L);
            assertThat(link.sessionId()).isEqualTo(sessionId);
        });

        ArgumentCaptor<String> lockOwner = ArgumentCaptor.forClass(String.class);
        verify(valueOperations).setIfAbsent(
                eq("notification:telegram-link:lock:{" + sessionId + "}:lock"), lockOwner.capture(), eq(Duration.ofSeconds(30)));
        assertThat(lockOwner.getValue()).isNotEqualTo("1");
    }

    @Test
    void returnsExpiredWhenRedisStatusTtlHasElapsed() {
        when(valueOperations.get(any())).thenReturn(null);

        assertThat(repository.status(7L, UUID.randomUUID()))
                .isEqualTo(TelegramLinkRedisRepository.LinkStatus.EXPIRED);
    }

    @Test
    void returnsExpiredForStatusLookupByAnotherUser() {
        UUID sessionId = UUID.randomUUID();
        when(valueOperations.get("notification:telegram-link:status:{" + sessionId + "}:status"))
                .thenReturn("7:" + sessionId + ":PENDING");

        assertThat(repository.status(8L, sessionId)).isEqualTo(TelegramLinkRedisRepository.LinkStatus.EXPIRED);
    }

    @Test
    void marksLinkedAndConsumesTokenInOneRedisScript() {
        UUID sessionId = UUID.randomUUID();
        TelegramLinkRedisRepository.PendingLink pendingLink =
                new TelegramLinkRedisRepository.PendingLink("hash", 7L, sessionId);

        repository.markLinked(pendingLink, expiration);

        verify(redisTemplate).execute(
                any(),
                eq(java.util.List.of(
                        "notification:telegram-link:status:{" + sessionId + "}:status",
                        "notification:telegram-link:token:{" + sessionId + "}:token",
                        "notification:telegram-link:lock:{" + sessionId + "}:lock")),
                eq("7:" + sessionId + ":LINKED"),
                eq("600"), eq("test-lock-owner"));
    }

    @Test
    void marksExpiredAndConsumesTokenInOneRedisScript() {
        UUID sessionId = UUID.randomUUID();
        TelegramLinkRedisRepository.PendingLink pendingLink =
                new TelegramLinkRedisRepository.PendingLink("hash", 7L, sessionId);

        repository.markExpired(pendingLink, expiration);

        verify(redisTemplate).execute(
                any(),
                eq(java.util.List.of(
                        "notification:telegram-link:status:{" + sessionId + "}:status",
                        "notification:telegram-link:token:{" + sessionId + "}:token",
                        "notification:telegram-link:lock:{" + sessionId + "}:lock")),
                eq("7:" + sessionId + ":EXPIRED"),
                eq("600"), eq("test-lock-owner"));
    }

    @Test
    void doesNotReadTokenWhenLockIsAlreadyHeld() {
        UUID sessionId = TelegramLinkRedisRepository.sessionIdForTokenHash("hash");
        when(valueOperations.setIfAbsent(
                eq("notification:telegram-link:lock:{" + sessionId + "}:lock"), any(), eq(Duration.ofSeconds(30))))
                .thenReturn(false);

        assertThat(repository.acquire("hash", expiration)).isEmpty();

        verify(valueOperations, never()).get("notification:telegram-link:token:{" + sessionId + "}:token");
    }

    @Test
    void releasesOnlyTheLockOwnedByTheCurrentRequestWhenTokenWasAlreadyConsumed() {
        UUID sessionId = TelegramLinkRedisRepository.sessionIdForTokenHash("hash");
        when(valueOperations.setIfAbsent(
                eq("notification:telegram-link:lock:{" + sessionId + "}:lock"), any(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(valueOperations.get("notification:telegram-link:token:{" + sessionId + "}:token")).thenReturn(null);

        assertThat(repository.acquire("hash", expiration)).isEmpty();

        verify(redisTemplate).execute(any(), eq(java.util.List.of(
                "notification:telegram-link:lock:{" + sessionId + "}:lock")), any());
    }

    @Test
    void removesCorruptClusterCompatibleTokenAndReleasesOnlyOwnedLock() {
        UUID sessionId = TelegramLinkRedisRepository.sessionIdForTokenHash("hash");
        when(valueOperations.setIfAbsent(
                eq("notification:telegram-link:lock:{" + sessionId + "}:lock"), any(), eq(Duration.ofSeconds(30))))
                .thenReturn(true);
        when(valueOperations.get("notification:telegram-link:token:{" + sessionId + "}:token")).thenReturn("garbage");

        assertThat(repository.acquire("hash", expiration)).isEmpty();

        verify(redisTemplate).delete("notification:telegram-link:token:{" + sessionId + "}:token");
        verify(redisTemplate).execute(any(), eq(java.util.List.of(
                "notification:telegram-link:lock:{" + sessionId + "}:lock")), any());
    }
}
