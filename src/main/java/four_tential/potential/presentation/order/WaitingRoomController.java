package four_tential.potential.presentation.order;

import four_tential.potential.application.order.WaitingListService;
import four_tential.potential.common.dto.BaseResponse;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.infra.sse.SseWaitingEventPublisher;
import four_tential.potential.infra.sse.SseWaitingRoomRepository;
import four_tential.potential.presentation.order.dto.WaitingRoomEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("/v1/orders/waiting-room")
@RequiredArgsConstructor
public class WaitingRoomController {

    private final WaitingListService waitingListService;
    private final SseWaitingRoomRepository sseWaitingRoomRepository;
    private final SseWaitingEventPublisher sseWaitingEventPublisher;

    /**
     * 대기열 실시간 스트리밍 연결 (SSE)
     */
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('STUDENT')")
    public SseEmitter streamWaitingStatus(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam UUID courseId
    ) {
        UUID memberId = principal.memberId();
        // 30분 타임아웃 (대기열 만료 시간)
        SseEmitter emitter = new SseEmitter(30 * 60 * 1000L);
        
        sseWaitingRoomRepository.save(courseId, memberId, emitter);

        emitter.onCompletion(() -> sseWaitingRoomRepository.delete(courseId, memberId));
        emitter.onTimeout(() -> {
            emitter.complete();
        });

        // 연결 즉시 현재 순번 전송
        Long rank = waitingListService.getWaitingRank(courseId, memberId);
        int totalWaitingCount = waitingListService.getWaitingListSize(courseId);
        
        if (rank != null) {
            sseWaitingEventPublisher.publish(courseId, memberId, 
                    WaitingRoomEventResponse.waiting(courseId, memberId, rank, totalWaitingCount));
        }

        return emitter;
    }

    /**
     * 대기열 수동 이탈
     */
    @DeleteMapping
    @PreAuthorize("hasRole('STUDENT')")
    public ResponseEntity<BaseResponse<Void>> leaveWaitingRoom(
            @AuthenticationPrincipal MemberPrincipal principal,
            @RequestParam UUID courseId
    ) {
        waitingListService.removeFromWaitingList(courseId, principal.memberId());
        sseWaitingRoomRepository.delete(courseId, principal.memberId());
        
        return ResponseEntity.ok(BaseResponse.success(HttpStatus.OK.name(), "대기열에서 이탈하였습니다", null));
    }
}
