package four_tential.potential.infra.redis.qr;

import four_tential.potential.infra.qr.QrTokenRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static four_tential.potential.infra.redis.RedisConstants.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QrTokenRepositoryTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private QrTokenRepository qrTokenRepository;

    private static final UUID COURSE_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String TOKEN   = "test-qr-token-uuid";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Nested
    @DisplayName("saveIfAbsent() - QR 토큰 원자적 저장")
    class SaveIfAbsentTest {

        @Test
        @DisplayName("활성 QR 이 없으면 저장 후 true 를 반환한다")
        void saveIfAbsent_returnsTrue() {
            // given
            when(valueOperations.setIfAbsent(
                    QR_ATTENDANCE_PREFIX + COURSE_ID,
                    TOKEN,
                    QR_TTL_SECONDS, TimeUnit.SECONDS
            )).thenReturn(true);

            // when
            boolean result = qrTokenRepository.saveIfAbsent(COURSE_ID, TOKEN);

            // then
            assertThat(result).isTrue();
            verify(valueOperations).set(
                    QR_TOKEN_PREFIX + TOKEN,
                    COURSE_ID.toString(),
                    QR_TTL_SECONDS, TimeUnit.SECONDS
            );
        }

        @Test
        @DisplayName("활성 QR 이 이미 존재하면 false 를 반환하고 token 키를 저장하지 않는다")
        void saveIfAbsent_returnsFalse() {
            // given
            when(valueOperations.setIfAbsent(
                    QR_ATTENDANCE_PREFIX + COURSE_ID,
                    TOKEN,
                    QR_TTL_SECONDS, TimeUnit.SECONDS
            )).thenReturn(false);

            // when
            boolean result = qrTokenRepository.saveIfAbsent(COURSE_ID, TOKEN);

            // then
            assertThat(result).isFalse();
            verify(valueOperations, never()).set(
                    eq(QR_TOKEN_PREFIX + TOKEN),
                    anyString(),
                    anyLong(), any()
            );
        }

        @Test
        @DisplayName("Redis 가 null 을 반환하면 false 를 반환한다")
        void saveIfAbsent_nullReturnsFalse() {
            // given
            when(valueOperations.setIfAbsent(
                    QR_ATTENDANCE_PREFIX + COURSE_ID,
                    TOKEN,
                    QR_TTL_SECONDS, TimeUnit.SECONDS
            )).thenReturn(null);

            // when
            boolean result = qrTokenRepository.saveIfAbsent(COURSE_ID, TOKEN);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("TTL 은 600초로 설정된다")
        void saveIfAbsent_ttlIs600Seconds() {
            // given
            when(valueOperations.setIfAbsent(
                    anyString(), anyString(), eq(600L), eq(TimeUnit.SECONDS)
            )).thenReturn(true);

            // when
            qrTokenRepository.saveIfAbsent(COURSE_ID, TOKEN);

            // then
            verify(valueOperations).setIfAbsent(
                    anyString(), anyString(), eq(600L), eq(TimeUnit.SECONDS)
            );
        }
    }

    @Nested
    @DisplayName("findCourseIdByToken() - 토큰으로 courseId 역조회")
    class FindCourseIdByTokenTest {

        @Test
        @DisplayName("유효한 토큰이면 courseId 를 Optional 로 반환한다")
        void findCourseIdByToken_returnsOptionalCourseId() {
            // given
            when(valueOperations.get(QR_TOKEN_PREFIX + TOKEN))
                    .thenReturn(COURSE_ID.toString());

            // when
            Optional<UUID> result = qrTokenRepository.findCourseIdByToken(TOKEN);

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(COURSE_ID);
        }

        @Test
        @DisplayName("토큰이 만료됐거나 없으면 Optional.empty() 를 반환한다")
        void findCourseIdByToken_returnsEmpty() {
            // given
            when(valueOperations.get(QR_TOKEN_PREFIX + TOKEN)).thenReturn(null);

            // when
            Optional<UUID> result = qrTokenRepository.findCourseIdByToken(TOKEN);

            // then
            assertThat(result).isEmpty();
        }
    }
}