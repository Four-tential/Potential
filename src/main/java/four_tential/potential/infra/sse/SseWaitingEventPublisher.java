package four_tential.potential.infra.sse;

import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.domain.order.WaitingStatus;
import four_tential.potential.presentation.order.dto.WaitingRoomEventResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.UUID;

@Slf4j
@Component
public class SseWaitingEventPublisher {

    private final SseWaitingRoomRepository sseWaitingRoomRepository;
    private final WaitingListService waitingListService;

    public SseWaitingEventPublisher(
            SseWaitingRoomRepository sseWaitingRoomRepository,
            @Lazy WaitingListService waitingListService
    ) {
        this.sseWaitingRoomRepository = sseWaitingRoomRepository;
        this.waitingListService = waitingListService;
    }

    /**
     * 특정 사용자에게 대기열 상태 전송
     */
    public void publish(UUID courseId, UUID memberId, WaitingRoomEventResponse response) {
        sseWaitingRoomRepository.find(courseId, memberId).ifPresent(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("waiting-room")
                        .data(response));
                
                if (WaitingStatus.CALLED.equals(response.status())) {
                    emitter.complete();
                    sseWaitingRoomRepository.delete(courseId, memberId, emitter);
                }
            } catch (IOException e) {
                log.warn("SSE 전송 중 IO 오류: courseId={}, memberId={}", courseId, memberId, e);
                handleDisconnect(courseId, memberId, emitter);
            } catch (Exception e) {
                log.error("SSE 전송 실패: courseId={}, memberId={}", courseId, memberId, e);
                handleDisconnect(courseId, memberId, emitter);
            }
        });
    }

    /**
     * 하트비트 전송
     */
    public void sendHeartbeat(UUID courseId, UUID memberId) {
        sseWaitingRoomRepository.find(courseId, memberId).ifPresent(emitter -> {
            try {
                emitter.send(SseEmitter.event()
                        .name("heartbeat")
                        .data("ping"));
            } catch (IOException e) {
                handleDisconnect(courseId, memberId, emitter);
            } catch (Exception e) {
                handleDisconnect(courseId, memberId, emitter);
            }
        });
    }

    private void handleDisconnect(UUID courseId, UUID memberId, SseEmitter emitter) {
        log.warn("SSE 연결 끊김 감지: 대기열 자동 이탈 처리. courseId={}, memberId={}", courseId, memberId);
        sseWaitingRoomRepository.delete(courseId, memberId, emitter);
        try {
            waitingListService.removeFromWaitingList(courseId, memberId);
        } catch (Exception e) {
            log.error("대기열 이탈 처리 실패: courseId={}, memberId={}", courseId, memberId, e);
        }
        try {
            emitter.complete();
        } catch (Exception ignored) {}
    }
}
