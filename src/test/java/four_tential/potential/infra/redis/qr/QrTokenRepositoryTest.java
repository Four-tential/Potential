package four_tential.potential.infra.redis.qr;

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
    @DisplayName("save() - QR 토큰 저장")
    class SaveTest {

        @Test
        @DisplayName("courseId 기준 키와 token 기준 키 두 개를 저장한다")
        void save_storesTwoKeys() {
            // when
            qrTokenRepository.save(COURSE_ID, TOKEN);

            // then
            verify(valueOperations).set(
                    QR_ATTENDANCE_PREFIX + COURSE_ID,
                    TOKEN,
                    QR_TTL_SECONDS, TimeUnit.SECONDS
            );
            verify(valueOperations).set(
                    QR_TOKEN_PREFIX + TOKEN,
                    COURSE_ID.toString(),
                    QR_TTL_SECONDS, TimeUnit.SECONDS
            );
        }

        @Test
        @DisplayName("TTL 은 600초로 설정된다")
        void save_ttlIs600Seconds() {
            // when
            qrTokenRepository.save(COURSE_ID, TOKEN);

            // then
            verify(valueOperations, times(2)).set(
                    anyString(), anyString(), eq(600L), eq(TimeUnit.SECONDS)
            );
        }
    }

    @Nested
    @DisplayName("existsByCourseId() - 활성 QR 존재 여부")
    class ExistsByCourseIdTest {

        @Test
        @DisplayName("활성 QR 이 존재하면 true 를 반환한다")
        void existsByCourseId_returnsTrue() {
            // given
            when(redisTemplate.hasKey(QR_ATTENDANCE_PREFIX + COURSE_ID)).thenReturn(true);

            // when
            boolean result = qrTokenRepository.existsByCourseId(COURSE_ID);

            // then
            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("활성 QR 이 없으면 false 를 반환한다")
        void existsByCourseId_returnsFalse() {
            // given
            when(redisTemplate.hasKey(QR_ATTENDANCE_PREFIX + COURSE_ID)).thenReturn(false);

            // when
            boolean result = qrTokenRepository.existsByCourseId(COURSE_ID);

            // then
            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("Redis 가 null 을 반환하면 false 를 반환한다")
        void existsByCourseId_nullReturnsFalse() {
            // given
            when(redisTemplate.hasKey(QR_ATTENDANCE_PREFIX + COURSE_ID)).thenReturn(null);

            // when
            boolean result = qrTokenRepository.existsByCourseId(COURSE_ID);

            // then
            assertThat(result).isFalse();
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