package four_tential.potential.infra.sse;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.presentation.attendance.dto.AttendanceEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class SseAttendanceEventPublisher {

    private final SseEmitterRepository sseEmitterRepository;

    public void publish(UUID courseId, Attendance attendance) {
        sseEmitterRepository.findByCourseId(courseId).ifPresent(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("attendance")
                        .data(AttendanceEventResponse.from(attendance)));

            } catch (IOException e) {
                log.warn("SSE 클라이언트 연결 끊김 courseId={} memberId={}",
                        courseId, attendance.getMemberId());
                sseEmitterRepository.delete(courseId);
                emitter.complete();
            } catch (Exception e) {
                log.error("SSE 이벤트 전송 실패 courseId={}", courseId, e);
                sseEmitterRepository.delete(courseId);
                emitter.completeWithError(e);
            }
        });
    }
}