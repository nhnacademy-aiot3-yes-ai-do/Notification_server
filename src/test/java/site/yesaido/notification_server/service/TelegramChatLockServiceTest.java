package site.yesaido.notification_server.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;


import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TelegramChatLockServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private TelegramChatLockService lockService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lockService = new TelegramChatLockService(redisTemplate);
    }

    @Test
    @DisplayName("락 즉시 획득에 성공하면 true를 반환한다")
    void tryLock_whenAcquiredImmediately_returnsTrue() {
        when(valueOperations.setIfAbsent("notification:telegram:chat-lock:12345", "owner-1", Duration.ofSeconds(60)))
                .thenReturn(true);

        boolean result = lockService.tryLock(12345L, "owner-1", Duration.ofMillis(200), Duration.ofSeconds(60));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("락 획득을 재시도하여 대기 중 획득에 성공하면 true를 반환한다")
    void tryLock_whenAcquiredAfterRetry_returnsTrue() {
        when(valueOperations.setIfAbsent("notification:telegram:chat-lock:12345", "owner-1", Duration.ofSeconds(60)))
                .thenReturn(false)
                .thenReturn(true);

        boolean result = lockService.tryLock(12345L, "owner-1", Duration.ofMillis(300), Duration.ofSeconds(60));

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("waitTimeout 동안 락을 획득하지 못하면 false를 반환한다")
    void tryLock_whenTimeoutExpires_returnsFalse() {
        when(valueOperations.setIfAbsent("notification:telegram:chat-lock:12345", "owner-1", Duration.ofSeconds(60)))
                .thenReturn(false);

        boolean result = lockService.tryLock(12345L, "owner-1", Duration.ofMillis(150), Duration.ofSeconds(60));

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("입력 파라미터가 null이거나 유효하지 않은 시간(음수, 0)이면 false를 반환한다")
    void tryLock_whenInvalidParameters_returnsFalse() {
        assertThat(lockService.tryLock(null, "owner", Duration.ofSeconds(1), Duration.ofSeconds(60))).isFalse();
        assertThat(lockService.tryLock(12345L, null, Duration.ofSeconds(1), Duration.ofSeconds(60))).isFalse();
        assertThat(lockService.tryLock(12345L, "owner", null, Duration.ofSeconds(60))).isFalse();
        assertThat(lockService.tryLock(12345L, "owner", Duration.ofSeconds(1), null)).isFalse();
        assertThat(lockService.tryLock(12345L, "owner", Duration.ofSeconds(-1), Duration.ofSeconds(60))).isFalse();
        assertThat(lockService.tryLock(12345L, "owner", Duration.ofSeconds(1), Duration.ZERO)).isFalse();
        assertThat(lockService.tryLock(12345L, "owner", Duration.ofSeconds(1), Duration.ofSeconds(-10))).isFalse();
    }

    @Test
    @DisplayName("락 해제 시 Lua 스크립트를 호출한다")
    void releaseLock_executesScript() {
        lockService.releaseLock(12345L, "owner-1");
        verify(redisTemplate).execute(
                any(),
                eq(List.of("notification:telegram:chat-lock:12345")),
                eq("owner-1")
        );
    }

    @Test
    @DisplayName("chatId 또는 lockOwner가 null이면 락 해제를 수행하지 않는다")
    void releaseLock_whenNullParameters_doesNothing() {
        lockService.releaseLock(null, "owner-1");
        lockService.releaseLock(12345L, null);

        verify(redisTemplate, never()).execute(any(), anyList(), any());
    }

    @Test
    @DisplayName("Redis 예외 발생 시 에러를 로깅하고 정상 반환한다")
    void releaseLock_whenRedisThrows_catchesGracefully() {
        doThrow(new RuntimeException("Redis connection error"))
                .when(redisTemplate).execute(any(), anyList(), any());

        assertDoesNotThrow(() -> lockService.releaseLock(12345L, "owner-1"));
    }
}
