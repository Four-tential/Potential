package four_tential.potential.infra.ai.chatbot;

import four_tential.potential.common.exception.ServiceErrorException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.contains;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatbotRateLimiterTest {

    private RedissonClient redissonClient;
    private RScoredSortedSet<String> minuteBucket;
    private RScoredSortedSet<String> dayBucket;
    private MeterRegistry meterRegistry;
    private ChatbotRateLimiter limiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redissonClient = mock(RedissonClient.class);
        minuteBucket = mock(RScoredSortedSet.class);
        dayBucket = mock(RScoredSortedSet.class);
        meterRegistry = new SimpleMeterRegistry();

        doReturn(minuteBucket).when(redissonClient).getScoredSortedSet(contains(":min:"), any());
        doReturn(dayBucket).when(redissonClient).getScoredSortedSet(contains(":day:"), any());

        limiter = new ChatbotRateLimiter(redissonClient, meterRegistry);
    }

    @Test
    @DisplayName("관리자는 한도 검사 우회")
    void admin_bypasses_rate_limit() {
        limiter.check(UUID.randomUUID(), "ROLE_ADMIN");

        verify(redissonClient, never()).getScoredSortedSet(anyString(), any());
    }

    @Test
    @DisplayName("분/일 한도 모두 여유 — 통과 시 두 버킷에 모두 ZADD")
    void within_limits_adds_to_both_buckets() {
        when(minuteBucket.size()).thenReturn(0);
        when(dayBucket.size()).thenReturn(0);

        limiter.check(UUID.randomUUID(), "ROLE_USER");

        verify(minuteBucket).add(anyDouble(), anyString());
        verify(dayBucket).add(anyDouble(), anyString());
    }

    @Test
    @DisplayName("분당 한도 초과 — ServiceErrorException + minute 카운터 증가")
    void minute_limit_exceeded_throws() {
        long now = System.currentTimeMillis();
        when(minuteBucket.size()).thenReturn(ChatbotRateLimiter.MINUTE_LIMIT);
        when(minuteBucket.firstScore()).thenReturn((double) (now - 30_000L));

        assertThatThrownBy(() -> limiter.check(UUID.randomUUID(), "ROLE_USER"))
                .isInstanceOf(ServiceErrorException.class);

        verify(minuteBucket, never()).add(anyDouble(), anyString());
        verify(dayBucket, never()).add(anyDouble(), anyString());

        Counter counter = meterRegistry.find("chatbot.rate_limit.exceeded").tag("window", "minute").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("분당 통과 + 일일 초과 — ServiceErrorException")
    void day_limit_exceeded_throws() {
        long now = System.currentTimeMillis();
        when(minuteBucket.size()).thenReturn(0);
        when(dayBucket.size()).thenReturn(ChatbotRateLimiter.DAY_LIMIT);
        when(dayBucket.firstScore()).thenReturn((double) (now - 1_000L));

        assertThatThrownBy(() -> limiter.check(UUID.randomUUID(), "ROLE_USER"))
                .isInstanceOf(ServiceErrorException.class);

        verify(minuteBucket).add(anyDouble(), anyString());
        verify(dayBucket, never()).add(anyDouble(), anyString());

        Counter counter = meterRegistry.find("chatbot.rate_limit.exceeded").tag("window", "day").counter();
        assertThat(counter).isNotNull();
        assertThat(counter.count()).isEqualTo(1.0);
    }

    @Test
    @DisplayName("버킷 expire 호출로 TTL 보장")
    void sets_expire_on_bucket() {
        when(minuteBucket.size()).thenReturn(0);
        when(dayBucket.size()).thenReturn(0);

        limiter.check(UUID.randomUUID(), "ROLE_USER");

        verify(minuteBucket, atLeastOnce()).expire(any(Duration.class));
        verify(dayBucket, atLeastOnce()).expire(any(Duration.class));
    }

    @Test
    @DisplayName("removeRangeByScore 로 윈도우 밖 항목 제거")
    void evicts_old_entries_before_count() {
        when(minuteBucket.size()).thenReturn(0);
        when(dayBucket.size()).thenReturn(0);

        limiter.check(UUID.randomUUID(), "ROLE_USER");

        verify(minuteBucket).removeRangeByScore(eq(0.0), eq(true), anyDouble(), eq(true));
        verify(dayBucket).removeRangeByScore(eq(0.0), eq(true), anyDouble(), eq(true));
    }
}
