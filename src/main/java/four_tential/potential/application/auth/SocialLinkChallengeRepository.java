package four_tential.potential.application.auth;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static four_tential.potential.infra.redis.RedisConstants.SOCIAL_LINK_ATTEMPTS_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.SOCIAL_LINK_CHALLENGE_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.SOCIAL_LINK_CHALLENGE_TTL_SECONDS;
import static four_tential.potential.infra.redis.RedisConstants.SOCIAL_LINK_MAX_ATTEMPTS;

@Repository
@RequiredArgsConstructor
public class SocialLinkChallengeRepository {

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTE_LENGTH = 32;

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public String issue(SocialLinkChallengeData data) {
        String token = generateToken();
        try {
            String json = objectMapper.writeValueAsString(data);
            redisTemplate.opsForValue().set(
                    SOCIAL_LINK_CHALLENGE_PREFIX + token,
                    json,
                    SOCIAL_LINK_CHALLENGE_TTL_SECONDS,
                    TimeUnit.SECONDS
            );
            return token;
        } catch (JacksonException e) {
            throw new IllegalStateException("소셜 연동 챌린지 직렬화 실패", e);
        }
    }

    public Optional<SocialLinkChallengeData> peek(String token) {
        String dataKey = SOCIAL_LINK_CHALLENGE_PREFIX + token;
        String attemptsKey = SOCIAL_LINK_ATTEMPTS_PREFIX + token;

        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        if (attempts != null && attempts == 1L) {
            redisTemplate.expire(attemptsKey, SOCIAL_LINK_CHALLENGE_TTL_SECONDS, TimeUnit.SECONDS);
        }
        if (attempts != null && attempts > SOCIAL_LINK_MAX_ATTEMPTS) {
            redisTemplate.delete(dataKey);
            redisTemplate.delete(attemptsKey);
            return Optional.empty();
        }

        String json = redisTemplate.opsForValue().get(dataKey);
        if (json == null) {
            redisTemplate.delete(attemptsKey);
            return Optional.empty();
        }
        try {
            return Optional.of(objectMapper.readValue(json, SocialLinkChallengeData.class));
        } catch (JacksonException e) {
            throw new IllegalStateException("소셜 연동 챌린지 역직렬화 실패", e);
        }
    }

    public void invalidate(String token) {
        redisTemplate.delete(SOCIAL_LINK_CHALLENGE_PREFIX + token);
        redisTemplate.delete(SOCIAL_LINK_ATTEMPTS_PREFIX + token);
    }

    private String generateToken() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
