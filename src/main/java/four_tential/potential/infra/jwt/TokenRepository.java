package four_tential.potential.infra.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static four_tential.potential.infra.redis.RedisConstants.BLACK_LIST_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.REFRESH_TOKEN_PREFIX;

@Service
@RequiredArgsConstructor
public class TokenRepository {
    private final RedisTemplate<String, Object> redisTemplate;

    //region 토큰 관련
    public void saveRefreshToken(String email, String refreshToken, long expireTime) {
        redisTemplate.opsForValue().set(
                REFRESH_TOKEN_PREFIX + email
                , refreshToken
                , expireTime
                , TimeUnit.MILLISECONDS
        );
    }

    public String getRefreshToken(String email) {
        Object value = redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + email);
        return value != null ? value.toString() : null;
    }

    public void deleteRefreshToken(String email) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + email);
    }

    public void addBlacklist(String accessToken, long expireTime) {
        redisTemplate.opsForValue().set(
                BLACK_LIST_PREFIX + accessToken
                , "blacklist"
                , expireTime
                , TimeUnit.MILLISECONDS
        );
    }

    public boolean isBlacklist(String accessToken) {
        return redisTemplate.hasKey(BLACK_LIST_PREFIX + accessToken);
    }
    //endregion
}
