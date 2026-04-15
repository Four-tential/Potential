package four_tential.potential.infra.jwt;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.concurrent.TimeUnit;

import static four_tential.potential.infra.redis.RedisConstants.BLACK_LIST_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.REFRESH_TOKEN_PREFIX;

@Service
@RequiredArgsConstructor
public class JwtRepository {
    // " 가 역직렬화시 RedisTemplate<String, Object> 구조에서 붙어서 변경
    private final StringRedisTemplate redisTemplate;

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
        return redisTemplate.opsForValue().get(REFRESH_TOKEN_PREFIX + email);
    }

    public String getAndDeleteRefreshToken(String email) {
        return redisTemplate.opsForValue().getAndDelete(REFRESH_TOKEN_PREFIX + email);
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
