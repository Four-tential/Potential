package four_tential.potential.infra.jwt;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import static four_tential.potential.infra.redis.RedisConstants.BLACK_LIST_PREFIX;
import static four_tential.potential.infra.redis.RedisConstants.REFRESH_TOKEN_PREFIX;

@Slf4j
@Service
@RequiredArgsConstructor
public class JwtRepository {
    // RedisTemplate<String, Object> 사용 시 역직렬화 과정에서 값에 '"' 가 붙어
    // Refresh Token 불일치가 발생하므로 StringRedisTemplate 으로 변경
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

    public String getAndDeleteRefreshToken(String email) {
        return redisTemplate.opsForValue().getAndDelete(REFRESH_TOKEN_PREFIX + email);
    }

    public void deleteRefreshToken(String email) {
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + email);
    }

    public void deleteRefreshTokensByPrefix(String prefix) {
        String pattern = REFRESH_TOKEN_PREFIX + prefix + "*";
        Set<String> keys = new HashSet<>();
        
        ScanOptions options = ScanOptions.scanOptions().match(pattern).count(100).build();
        try (Cursor<String> cursor = redisTemplate.scan(options)) {
            while (cursor.hasNext()) {
                keys.add(cursor.next());
            }
        } catch (Exception e) {
            log.error("Redis SCAN 중 오류 발생: {}", e.getMessage());
            // SCAN 실패 시 KEYS로 폴백 (일부 환경용)
            Set<String> fallbackKeys = redisTemplate.keys(pattern);
            if (fallbackKeys != null) keys.addAll(fallbackKeys);
        }

        if (!keys.isEmpty()) {
            log.info("삭제할 리프레시 토큰 키 발견 (패턴: {}): {}개", pattern, keys.size());
            // 디버깅을 위해 처음 5개 키만 로그 출력
            keys.stream().limit(5).forEach(k -> log.info("삭제 대상 키 예시: {}", k));
            redisTemplate.delete(keys);
        } else {
            log.warn("삭제할 리프레시 토큰 키를 찾지 못했습니다 (패턴: {})", pattern);
        }
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
