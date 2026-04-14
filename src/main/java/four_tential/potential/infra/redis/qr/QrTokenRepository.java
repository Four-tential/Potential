package four_tential.potential.infra.redis.qr;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static four_tential.potential.infra.redis.RedisConstants.*;

@Repository
@RequiredArgsConstructor
public class QrTokenRepository {

    private final RedisTemplate<String, Object> redisTemplate;

    // QR 생성 시 두 키 동시 저장
    public void save(UUID courseId, String token) {
        // courseId 기준 키
        redisTemplate.opsForValue().set(
                QR_ATTENDANCE_PREFIX + courseId,
                token,
                QR_TTL_SECONDS,
                TimeUnit.SECONDS
        );
        // token 기준 키
        redisTemplate.opsForValue().set(
                QR_TOKEN_PREFIX + token,
                courseId.toString(),
                QR_TTL_SECONDS,
                TimeUnit.SECONDS
        );
    }

    // 코스 기준 활성 QR 존재 여부 (중복 생성 방지)
    public boolean existsByCourseId(UUID courseId) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(QR_ATTENDANCE_PREFIX + courseId));
    }

    // 스캔 시 token -> courseId 역조회
    public Optional<UUID> findCourseIdByToken(String token) {
        Object value = redisTemplate.opsForValue().get(QR_TOKEN_PREFIX + token);
        return Optional.ofNullable(value)
                .map(v -> UUID.fromString(v.toString()));
    }
}
