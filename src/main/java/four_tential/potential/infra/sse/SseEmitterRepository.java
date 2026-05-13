package four_tential.potential.infra.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SseEmitterRepository {

    // 추후 수강생 등 다른 역할로 확장 시 key 구조를 변경할 수 있도록 courseId 기준으로 관리
    private final Map<UUID, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void save(UUID courseId, SseEmitter emitter) {
        emitters.put(courseId, emitter);
    }

    public Optional<SseEmitter> findByCourseId(UUID courseId) {
        return Optional.ofNullable(emitters.get(courseId));
    }

    public void delete(UUID courseId) {
        emitters.remove(courseId);
    }

    // 동일한 emitter 인스턴스일 때만 삭제 (중복 접속 시 새 연결 보호)
    public void deleteIfSame(UUID courseId, SseEmitter emitter) {
        emitters.remove(courseId, emitter);
    }
}