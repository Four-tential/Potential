package four_tential.potential.infra.ai.chatbot;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.ChatbotExceptionEnum;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RScoredSortedSet;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

@Slf4j
@Component
@Profile("!test")
public class ChatbotRateLimiter {

    static final String ROLE_ADMIN = "ROLE_ADMIN";
    static final long MINUTE_WINDOW_MS = 60_000L;
    static final int MINUTE_LIMIT = 10;
    static final long DAY_WINDOW_MS = 86_400_000L;
    static final int DAY_LIMIT = 100;

    private final RedissonClient redissonClient;
    private final Counter exceededMinuteCounter;
    private final Counter exceededDayCounter;

    public ChatbotRateLimiter(RedissonClient redissonClient, MeterRegistry meterRegistry) {
        this.redissonClient = redissonClient;
        this.exceededMinuteCounter = Counter.builder("chatbot.rate_limit.exceeded")
                .tag("window", "minute")
                .register(meterRegistry);
        this.exceededDayCounter = Counter.builder("chatbot.rate_limit.exceeded")
                .tag("window", "day")
                .register(meterRegistry);
    }

    public void check(UUID memberId, String role) {
        if (ROLE_ADMIN.equals(role)) {
            return;
        }

        long retryAfter = tryAcquire(minuteKey(memberId), MINUTE_WINDOW_MS, MINUTE_LIMIT);
        if (retryAfter > 0) {
            exceededMinuteCounter.increment();
            log.warn("챗봇 분당 한도 초과 — memberId={}, retryAfter={}s", memberId, retryAfter);
            throw new ServiceErrorException(ChatbotExceptionEnum.ERR_CHATBOT_RATE_LIMIT);
        }

        retryAfter = tryAcquire(dayKey(memberId), DAY_WINDOW_MS, DAY_LIMIT);
        if (retryAfter > 0) {
            exceededDayCounter.increment();
            log.warn("챗봇 일일 한도 초과 — memberId={}, retryAfter={}s", memberId, retryAfter);
            throw new ServiceErrorException(ChatbotExceptionEnum.ERR_CHATBOT_RATE_LIMIT);
        }
    }

    private long tryAcquire(String key, long windowMs, int limit) {
        RScoredSortedSet<String> bucket = redissonClient.getScoredSortedSet(key, StringCodec.INSTANCE);
        long now = System.currentTimeMillis();

        bucket.removeRangeByScore(0, true, (double) (now - windowMs), true);

        int count = bucket.size();
        if (count >= limit) {
            Double oldestScore = bucket.firstScore();
            if (oldestScore == null) {
                return 1L;
            }
            long retryAfterMs = (long) (oldestScore + windowMs - now);
            return Math.max(1L, (retryAfterMs + 999L) / 1000L);
        }

        bucket.add(now, now + ":" + UUID.randomUUID());
        bucket.expire(Duration.ofMillis(windowMs + 5_000L));
        return 0L;
    }

    private String minuteKey(UUID memberId) {
        return "chatbot:rate:min:" + memberId;
    }

    private String dayKey(UUID memberId) {
        return "chatbot:rate:day:" + memberId;
    }
}
