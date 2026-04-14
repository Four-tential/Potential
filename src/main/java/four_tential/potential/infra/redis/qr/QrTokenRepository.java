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

    public boolean saveIfAbsent(UUID courseId, String token) {
        // SETNX : 키가 없을 때만 저장, 성공하면 true / 이미 존재하면 false
        Boolean saved = redisTemplate.opsForValue().setIfAbsent(
                QR_ATTENDANCE_PREFIX + courseId,
                token,
                QR_TTL_SECONDS, TimeUnit.SECONDS
        );

        if (!Boolean.TRUE.equals(saved)) {
            return false; // 이미 활성 QR 존재
        }

        // courseId 키 저장 성공한 경우에만 token 역조회 키 저장
        redisTemplate.opsForValue().set(
                QR_TOKEN_PREFIX + token,
                courseId.toString(),
                QR_TTL_SECONDS, TimeUnit.SECONDS
        );
        return true;
    }

    // 스캔 시 token -> courseId 역조회
    public Optional<UUID> findCourseIdByToken(String token) {
        Object value = redisTemplate.opsForValue().get(QR_TOKEN_PREFIX + token);
        return Optional.ofNullable(value)
                .map(v -> UUID.fromString(v.toString()));
    }
}
