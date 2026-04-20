package four_tential.potential.application.order;

import four_tential.potential.infra.sse.SseWaitingEventPublisher;
import four_tential.potential.infra.sse.SseWaitingRoomRepository;
import four_tential.potential.presentation.order.dto.WaitingRoomEventResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
@Component
@RequiredArgsConstructor
public class WaitingRoomScheduler {

    private final WaitingListService waitingListService;
    private final SseWaitingRoomRepository sseWaitingRoomRepository;
    private final SseWaitingEventPublisher sseWaitingEventPublisher;

    /**
     * 5초마다 대기열 상태 업데이트 푸시 (하트비트 겸용)
     */
    @Scheduled(fixedDelay = 5000)
    public void pushWaitingStatusUpdates() {
        Map<UUID, Integer> waitingSizeCache = new HashMap<>();
        sseWaitingRoomRepository.getAllKeys().forEach(key -> {
            try {
                String[] parts = key.split(":",2);
                if (parts.length != 2) {
                    log.warn("대기열 키 형식 오류: key={}", key);
                    return;
                }
                UUID courseId = UUID.fromString(parts[0]);
                UUID memberId = UUID.fromString(parts[1]);

                Long rank = waitingListService.getWaitingRank(courseId, memberId);
                int totalWaitingCount = waitingSizeCache.computeIfAbsent(
                        courseId, waitingListService::getWaitingListSize
                );

                if (rank != null) {
                    sseWaitingEventPublisher.publish(courseId, memberId,
                            WaitingRoomEventResponse.waiting(courseId, memberId, rank, totalWaitingCount));
                } else {
                    // 대기열에 순번이 없으면 (이미 승격되었거나 만료됨) 하트비트로 연결 상태 확인 후 정리
                    sseWaitingEventPublisher.sendHeartbeat(courseId, memberId);
                }
            } catch (Exception e) {
                log.error("대기열 스케줄러 업데이트 실패: key={}", key, e);
            }
        });
    }
}
