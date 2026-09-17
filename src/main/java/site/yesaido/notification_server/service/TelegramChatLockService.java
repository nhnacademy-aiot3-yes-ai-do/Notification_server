package site.yesaido.notification_server.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

@Slf4j
@Service
public class TelegramChatLockService {
    private static final String LOCK_PREFIX = "notification:telegram:chat-lock:";
    private static final Duration POLL_INTERVAL = Duration.ofMillis(100);

    // [해결 1] path 파라미터 제거하고 내부에서 직접 스크립트 생성
    private static final DefaultRedisScript<Long> RELEASE_LOCK_SCRIPT = createReleaseLockScript();

    private final StringRedisTemplate redisTemplate;

    public TelegramChatLockService(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    private static DefaultRedisScript<Long> createReleaseLockScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("redis/telegram-link/release-lock.lua"));
        script.setResultType(Long.class);
        return script;
    }

    /**
     * chatId별 락 획득 시도 (최대 waitTimeout 동안 100ms 간격 스핀 대기)
     */
    @SuppressWarnings({"BusyWait", "java:S2276"}) // [해결 2] 분산 락 폴링 대기 루프 의도 명시
    public boolean tryLock(Long chatId, String lockOwner, Duration waitTimeout, Duration leaseTime) {
        if (chatId == null
                || lockOwner == null
                || waitTimeout == null
                || leaseTime == null
                || waitTimeout.isNegative()
                || leaseTime.isZero()
                || leaseTime.isNegative()) {
            return false;
        }

        String key = lockKey(chatId);
        long deadline = System.currentTimeMillis() + waitTimeout.toMillis();

        while (System.currentTimeMillis() <= deadline) {
            try {
                Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, lockOwner, leaseTime);
                if (Boolean.TRUE.equals(acquired)) {
                    return true;
                }
            } catch (Exception e) {
                log.error("Telegram chat lock Redis operation failed for chatId={}", chatId, e);
                return false;
            }

            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("Telegram chat lock acquisition interrupted for chatId={}", chatId);
                return false;
            }
        }

        return false;
    }

    /**
     * 소유자(lockOwner)가 일치할 때만 원자적으로 락 해제
     */
    public void releaseLock(Long chatId, String lockOwner) {
        if (chatId == null || lockOwner == null) {
            return;
        }

        try {
            redisTemplate.execute(RELEASE_LOCK_SCRIPT, List.of(lockKey(chatId)), lockOwner);
        } catch (Exception e) {
            log.error("Telegram chat lock release failed for chatId={}", chatId, e);
        }
    }

    private String lockKey(Long chatId) {
        return LOCK_PREFIX + chatId;
    }
}
