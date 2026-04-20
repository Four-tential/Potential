package four_tential.potential.infra.sse;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class SseWaitingRoomRepositoryTest {

    private SseWaitingRoomRepository sseWaitingRoomRepository;

    @BeforeEach
    void setUp() {
        sseWaitingRoomRepository = new SseWaitingRoomRepository();
    }

    @Test
    @DisplayName("SseEmitter를 성공적으로 저장하고 조회한다")
    void saveAndFind_success() {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        SseEmitter emitter = new SseEmitter();

        // when
        sseWaitingRoomRepository.save(courseId, memberId, emitter);
        Optional<SseEmitter> found = sseWaitingRoomRepository.find(courseId, memberId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(emitter);
    }

    @Test
    @DisplayName("이미 존재하는 키로 저장하면 이전 Emitter를 대체한다")
    void save_replaces_old_emitter() {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        SseEmitter oldEmitter = new SseEmitter();
        SseEmitter newEmitter = new SseEmitter();

        sseWaitingRoomRepository.save(courseId, memberId, oldEmitter);

        // when
        sseWaitingRoomRepository.save(courseId, memberId, newEmitter);
        Optional<SseEmitter> found = sseWaitingRoomRepository.find(courseId, memberId);

        // then
        assertThat(found).isPresent();
        assertThat(found.get()).isEqualTo(newEmitter);
        assertThat(found.get()).isNotEqualTo(oldEmitter);
    }

    @Test
    @DisplayName("특정 코스에 속한 모든 Emitter를 조회한다")
    void findAllByCourseId_success() {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId1 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();
        UUID otherCourseId = UUID.randomUUID();
        
        SseEmitter emitter1 = new SseEmitter();
        SseEmitter emitter2 = new SseEmitter();
        SseEmitter otherEmitter = new SseEmitter();

        sseWaitingRoomRepository.save(courseId, memberId1, emitter1);
        sseWaitingRoomRepository.save(courseId, memberId2, emitter2);
        sseWaitingRoomRepository.save(otherCourseId, UUID.randomUUID(), otherEmitter);

        // when
        Map<String, SseEmitter> result = sseWaitingRoomRepository.findAllByCourseId(courseId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result.values()).containsExactlyInAnyOrder(emitter1, emitter2);
    }

    @Test
    @DisplayName("키로만 Emitter를 삭제한다")
    void delete_by_key_success() {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        sseWaitingRoomRepository.save(courseId, memberId, new SseEmitter());

        // when
        sseWaitingRoomRepository.delete(courseId, memberId);
        Optional<SseEmitter> found = sseWaitingRoomRepository.find(courseId, memberId);

        // then
        assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("특정 Emitter 객체가 일치할 때만 삭제한다 (조건부 삭제)")
    void delete_with_emitter_match_success() {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        SseEmitter currentEmitter = new SseEmitter();
        sseWaitingRoomRepository.save(courseId, memberId, currentEmitter);

        SseEmitter otherEmitter = new SseEmitter();

        // when & then
        // 1. 다른 Emitter로 삭제 시도 -> 삭제되지 않아야 함
        sseWaitingRoomRepository.delete(courseId, memberId, otherEmitter);
        assertThat(sseWaitingRoomRepository.find(courseId, memberId)).isPresent();

        // 2. 현재 Emitter로 삭제 시도 -> 삭제되어야 함
        sseWaitingRoomRepository.delete(courseId, memberId, currentEmitter);
        assertThat(sseWaitingRoomRepository.find(courseId, memberId)).isEmpty();
    }

    @Test
    @DisplayName("저장된 모든 키 목록을 조회한다")
    void getAllKeys_success() {
        // given
        UUID courseId1 = UUID.randomUUID();
        UUID memberId1 = UUID.randomUUID();
        UUID courseId2 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();

        sseWaitingRoomRepository.save(courseId1, memberId1, new SseEmitter());
        sseWaitingRoomRepository.save(courseId2, memberId2, new SseEmitter());

        // when
        Set<String> keys = sseWaitingRoomRepository.getAllKeys();

        // then
        assertThat(keys).hasSize(2);
        assertThat(keys).contains(courseId1 + ":" + memberId1, courseId2 + ":" + memberId2);
    }
}
