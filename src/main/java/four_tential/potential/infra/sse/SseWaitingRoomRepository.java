package four_tential.potential.infra.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SseWaitingRoomRepository {

    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    public void save(UUID courseId, UUID memberId, SseEmitter emitter) {
        emitters.put(makeKey(courseId, memberId), emitter);
    }

    public Optional<SseEmitter> find(UUID courseId, UUID memberId) {
        return Optional.ofNullable(emitters.get(makeKey(courseId, memberId)));
    }

    public Map<String, SseEmitter> findAllByCourseId(UUID courseId) {
        Map<String, SseEmitter> courseEmitters = new ConcurrentHashMap<>();
        String prefix = courseId.toString() + ":";
        emitters.forEach((key, emitter) -> {
            if (key.startsWith(prefix)) {
                courseEmitters.put(key, emitter);
            }
        });
        return courseEmitters;
    }

    public void delete(UUID courseId, UUID memberId) {
        emitters.remove(makeKey(courseId, memberId));
    }

    public java.util.Set<String> getAllKeys() {
        return emitters.keySet();
    }

    private String makeKey(UUID courseId, UUID memberId) {
        return courseId.toString() + ":" + memberId.toString();
    }
}
