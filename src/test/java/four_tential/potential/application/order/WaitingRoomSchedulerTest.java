package four_tential.potential.application.order;

import four_tential.potential.infra.sse.SseWaitingEventPublisher;
import four_tential.potential.infra.sse.SseWaitingRoomRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class WaitingRoomSchedulerTest {

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private SseWaitingRoomRepository sseWaitingRoomRepository;

    @Mock
    private SseWaitingEventPublisher sseWaitingEventPublisher;

    @InjectMocks
    private WaitingRoomScheduler waitingRoomScheduler;

    @Test
    @DisplayName("스케줄러 실행 시 모든 활성 연결에 대해 상태 업데이트를 수행한다")
    void pushWaitingStatusUpdates_success() {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String key = courseId + ":" + memberId;
        
        given(sseWaitingRoomRepository.getAllKeys()).willReturn(Set.of(key));
        given(waitingListService.getWaitingRank(courseId, memberId)).willReturn(5L);
        given(waitingListService.getWaitingListSize(courseId)).willReturn(20);

        // when
        waitingRoomScheduler.pushWaitingStatusUpdates();

        // then
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(memberId), any());
    }

    @Test
    @DisplayName("순번 정보가 없으면 하트비트를 전송한다")
    void pushWaitingStatusUpdates_noRank_sendHeartbeat() {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId = UUID.randomUUID();
        String key = courseId + ":" + memberId;

        given(sseWaitingRoomRepository.getAllKeys()).willReturn(Set.of(key));
        given(waitingListService.getWaitingRank(courseId, memberId)).willReturn(null);

        // when
        waitingRoomScheduler.pushWaitingStatusUpdates();

        // then
        verify(sseWaitingEventPublisher).sendHeartbeat(courseId, memberId);
    }
}
