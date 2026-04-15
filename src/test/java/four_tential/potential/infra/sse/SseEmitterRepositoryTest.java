package four_tential.potential.infra.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRepositoryTest {

    private SseEmitterRepository sseEmitterRepository;

    private static final UUID COURSE_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        sseEmitterRepository = new SseEmitterRepository();
    }

    @Nested
    @DisplayName("save() - SseEmitter 저장")
    class SaveTest {

        @Test
        @DisplayName("저장 후 courseId 로 조회할 수 있다")
        void save_thenFind() {
            // given
            SseEmitter emitter = new SseEmitter();

            // when
            sseEmitterRepository.save(COURSE_ID, emitter);

            // then
            assertThat(sseEmitterRepository.findByCourseId(COURSE_ID)).isPresent();
        }

        @Test
        @DisplayName("동일한 courseId 로 저장하면 덮어쓴다")
        void save_overwrite() {
            // given
            SseEmitter first  = new SseEmitter();
            SseEmitter second = new SseEmitter();

            // when
            sseEmitterRepository.save(COURSE_ID, first);
            sseEmitterRepository.save(COURSE_ID, second);

            // then
            assertThat(sseEmitterRepository.findByCourseId(COURSE_ID).get()).isEqualTo(second);
        }
    }

    @Nested
    @DisplayName("findByCourseId() - SseEmitter 조회")
    class FindByCourseIdTest {

        @Test
        @DisplayName("저장된 emitter 를 반환한다")
        void findByCourseId_present() {
            // given
            SseEmitter emitter = new SseEmitter();
            sseEmitterRepository.save(COURSE_ID, emitter);

            // when
            Optional<SseEmitter> result = sseEmitterRepository.findByCourseId(COURSE_ID);

            // then
            assertThat(result).isPresent();
            assertThat(result.get()).isEqualTo(emitter);
        }

        @Test
        @DisplayName("존재하지 않으면 Optional.empty() 를 반환한다")
        void findByCourseId_empty() {
            // when
            Optional<SseEmitter> result = sseEmitterRepository.findByCourseId(COURSE_ID);

            // then
            assertThat(result).isEmpty();
        }
    }

    @Nested
    @DisplayName("delete() - SseEmitter 삭제")
    class DeleteTest {

        @Test
        @DisplayName("삭제 후 조회하면 Optional.empty() 를 반환한다")
        void delete_thenEmpty() {
            // given
            sseEmitterRepository.save(COURSE_ID, new SseEmitter());

            // when
            sseEmitterRepository.delete(COURSE_ID);

            // then
            assertThat(sseEmitterRepository.findByCourseId(COURSE_ID)).isEmpty();
        }
    }

    @Nested
    @DisplayName("deleteIfSame() - 동일 인스턴스일 때만 삭제")
    class DeleteIfSameTest {

        @Test
        @DisplayName("동일한 emitter 인스턴스이면 삭제한다")
        void deleteIfSame_sameInstance() {
            // given
            SseEmitter emitter = new SseEmitter();
            sseEmitterRepository.save(COURSE_ID, emitter);

            // when
            sseEmitterRepository.deleteIfSame(COURSE_ID, emitter);

            // then
            assertThat(sseEmitterRepository.findByCourseId(COURSE_ID)).isEmpty();
        }

        @Test
        @DisplayName("다른 emitter 인스턴스이면 삭제하지 않는다")
        void deleteIfSame_differentInstance() {
            // given
            SseEmitter current = new SseEmitter();  // 새 연결
            SseEmitter old     = new SseEmitter();  // 이전 연결
            sseEmitterRepository.save(COURSE_ID, current);

            // when — 이전 emitter 로 삭제 시도
            sseEmitterRepository.deleteIfSame(COURSE_ID, old);

            // then — 새 연결은 유지됨
            assertThat(sseEmitterRepository.findByCourseId(COURSE_ID)).isPresent();
            assertThat(sseEmitterRepository.findByCourseId(COURSE_ID).get()).isEqualTo(current);
        }
    }
}