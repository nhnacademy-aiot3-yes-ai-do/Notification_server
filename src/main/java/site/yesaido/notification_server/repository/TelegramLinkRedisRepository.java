package site.yesaido.notification_server.repository;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Repository;

@Repository
public class TelegramLinkRedisRepository {

    private static final Duration LOCK_EXPIRATION = Duration.ofSeconds(30);
    private static final String TOKEN_PREFIX = "notification:telegram-link:token:";
    private static final String STATUS_PREFIX = "notification:telegram-link:status:";
    private static final String LOCK_PREFIX = "notification:telegram-link:lock:";
    private static final String PENDING = "PENDING";
    private static final String LINKED = "LINKED";
    private static final String EXPIRED = "EXPIRED";
    private static final DefaultRedisScript<Long> CREATE_LINK = script("redis/telegram-link/create-link.lua");
    private static final DefaultRedisScript<Long> COMPLETE_LINK = script("redis/telegram-link/complete-link.lua");
    private static final DefaultRedisScript<Long> COMPLETE_AFTER_COMMIT =
            script("redis/telegram-link/complete-after-commit.lua");
    private static final DefaultRedisScript<Long> RENEW_PROCESSING =
            script("redis/telegram-link/renew-processing.lua");
    private static final DefaultRedisScript<Long> RELEASE_LOCK = script("redis/telegram-link/release-lock.lua");

    private final StringRedisTemplate redisTemplate;

    public TelegramLinkRedisRepository(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static DefaultRedisScript<Long> script(String path) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource(path));
        script.setResultType(Long.class);
        return script;
    }

    public void create(UUID sessionId, Long userId, Duration expiration) {
        String identity = userId + ":" + sessionId;
        redisTemplate.execute(
                CREATE_LINK,
                List.of(statusKey(sessionId), tokenKey(sessionId)),
                identity + ":" + PENDING,
                identity,
                expirationSeconds(expiration));
    }

    public Optional<PendingLink> acquire(String tokenHash, Duration processingExpiration) {
        UUID sessionId = sessionIdForTokenHash(tokenHash);
        String lockOwner = UUID.randomUUID().toString();
        String lockKey = lockKey(sessionId);
        if (!Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(lockKey, lockOwner, LOCK_EXPIRATION))) {
            return Optional.empty();
        }

        String value = redisTemplate.opsForValue().get(tokenKey(sessionId));
        if (value == null) {
            release(lockKey, lockOwner);
            return Optional.empty();
        }
        PendingLink pendingLink;
        try {
            pendingLink = PendingLink.parse(tokenHash, value, lockOwner);
        } catch (IllegalArgumentException exception) {
            redisTemplate.delete(tokenKey(sessionId));
            release(lockKey, lockOwner);
            return Optional.empty();
        }

        try {
            if (!renewProcessing(pendingLink, processingExpiration)) {
                release(pendingLink);
                return Optional.empty();
            }
            return Optional.of(pendingLink);
        } catch (RuntimeException exception) {
            try {
                release(pendingLink);
            } catch (RuntimeException releaseException) {
                exception.addSuppressed(releaseException);
            }
            throw exception;
        }
    }

    public LinkStatus status(Long userId, UUID sessionId) {
        String value = redisTemplate.opsForValue().get(statusKey(sessionId));
        if (value == null) {
            return LinkStatus.EXPIRED;
        }
        StoredStatus stored = StoredStatus.parse(value);
        if (!stored.userId().equals(userId)) {
            return LinkStatus.EXPIRED;
        }
        return LinkStatus.valueOf(stored.status());
    }

    public boolean markLinked(PendingLink pendingLink, Duration expiration) {
        return mark(pendingLink, LINKED, expiration);
    }

    public LinkCompletion completeLinkedAfterCommit(PendingLink pendingLink, Duration expiration) {
        Long result = redisTemplate.execute(
                COMPLETE_AFTER_COMMIT,
                List.of(
                        statusKey(pendingLink.sessionId()),
                        tokenKey(pendingLink.sessionId()),
                        lockKey(pendingLink.sessionId())),
                pendingLink.userId() + ":" + pendingLink.sessionId(),
                expirationSeconds(expiration),
                pendingLink.lockOwner());
        return LinkCompletion.from(result);
    }

    public boolean markExpired(PendingLink pendingLink, Duration expiration) {
        return mark(pendingLink, EXPIRED, expiration);
    }

    public void release(PendingLink pendingLink) {
        release(lockKey(pendingLink.sessionId()), pendingLink.lockOwner());
    }

    private boolean renewProcessing(PendingLink pendingLink, Duration processingExpiration) {
        return Long.valueOf(1L).equals(redisTemplate.execute(
                RENEW_PROCESSING,
                List.of(statusKey(pendingLink.sessionId()), tokenKey(pendingLink.sessionId()), lockKey(pendingLink.sessionId())),
                pendingLink.userId() + ":" + pendingLink.sessionId(),
                pendingLink.lockOwner(),
                expirationSeconds(processingExpiration)));
    }

    private boolean mark(PendingLink pendingLink, String status, Duration expiration) {
        return Long.valueOf(1L).equals(redisTemplate.execute(
                COMPLETE_LINK,
                List.of(
                        statusKey(pendingLink.sessionId()),
                        tokenKey(pendingLink.sessionId()),
                        lockKey(pendingLink.sessionId())),
                pendingLink.userId() + ":" + pendingLink.sessionId() + ":" + status,
                expirationSeconds(expiration),
                pendingLink.lockOwner()));
    }

    private String expirationSeconds(Duration expiration) {
        long seconds = expiration.toSeconds();
        if (seconds <= 0) {
            throw new IllegalArgumentException("Telegram link expiration must be at least one second");
        }
        return Long.toString(seconds);
    }

    private String tokenKey(UUID sessionId) {
        return TOKEN_PREFIX + "{" + sessionId + "}:token";
    }


    private String statusKey(UUID sessionId) {
        return STATUS_PREFIX + "{" + sessionId + "}:status";
    }

    private String lockKey(UUID sessionId) {
        return LOCK_PREFIX + "{" + sessionId + "}:lock";
    }

    private void release(String lockKey, String lockOwner) {
        redisTemplate.execute(RELEASE_LOCK, List.of(lockKey), lockOwner);
    }

    public static UUID sessionIdForTokenHash(String tokenHash) {
        return UUID.nameUUIDFromBytes(tokenHash.getBytes(StandardCharsets.UTF_8));
    }

    public enum LinkCompletion {
        REJECTED,
        TRANSITIONED,
        ALREADY_LINKED;

        private static LinkCompletion from(Long result) {
            if (Long.valueOf(1L).equals(result)) {
                return TRANSITIONED;
            }
            if (Long.valueOf(2L).equals(result)) {
                return ALREADY_LINKED;
            }
            return REJECTED;
        }
    }

    public enum LinkStatus {
        PENDING,
        LINKED,
        EXPIRED
    }

    public record PendingLink(String tokenHash, Long userId, UUID sessionId, String lockOwner) {
        public PendingLink(String tokenHash, Long userId, UUID sessionId) {
            this(tokenHash, userId, sessionId, "test-lock-owner");
        }

        private static PendingLink parse(String tokenHash, String value, String lockOwner) {
            String[] parts = value.split(":", -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("Invalid Telegram link cache value");
            }
            return new PendingLink(tokenHash, Long.parseLong(parts[0]), UUID.fromString(parts[1]), lockOwner);
        }
    }

    private record StoredStatus(Long userId, UUID sessionId, String status) {
        private static StoredStatus parse(String value) {
            String[] parts = value.split(":", -1);
            if (parts.length != 3) {
                throw new IllegalArgumentException("Invalid Telegram link status cache value");
            }
            return new StoredStatus(Long.parseLong(parts[0]), UUID.fromString(parts[1]), parts[2]);
        }
    }
}
