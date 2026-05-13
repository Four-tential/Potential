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
import static org.mockito.Mockito.*;

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
    @DisplayName("순번 정보가 없으면 하트비트를 전송하며, 대기열 총 인원을 조회하지 않는다")
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
        // 최적화: rank가 null이면 getWaitingListSize를 호출하지 않아야 함
        verify(waitingListService, never()).getWaitingListSize(any());
    }

    @Test
    @DisplayName("잘못된 키 형식은 무시한다")
    void pushWaitingStatusUpdates_invalidKey_ignored() {
        // given
        given(sseWaitingRoomRepository.getAllKeys()).willReturn(Set.of("invalidKey", "courseId:memberId:extra"));

        // when
        waitingRoomScheduler.pushWaitingStatusUpdates();

        // then
        verifyNoInteractions(waitingListService);
        verifyNoInteractions(sseWaitingEventPublisher);
    }

    @Test
    @DisplayName("동일한 코스 ID에 대해서는 대기열 총 인원 조회를 한 번만 수행한다 (캐싱 검증)")
    void pushWaitingStatusUpdates_caching_success() {
        // given
        UUID courseId = UUID.randomUUID();
        UUID memberId1 = UUID.randomUUID();
        UUID memberId2 = UUID.randomUUID();
        String key1 = courseId + ":" + memberId1;
        String key2 = courseId + ":" + memberId2;

        given(sseWaitingRoomRepository.getAllKeys()).willReturn(Set.of(key1, key2));
        given(waitingListService.getWaitingRank(eq(courseId), any(UUID.class))).willReturn(5L);
        given(waitingListService.getWaitingListSize(courseId)).willReturn(20);

        // when
        waitingRoomScheduler.pushWaitingStatusUpdates();

        // then
        // getWaitingListSize는 두 명의 유저가 있어도 코스가 같으므로 1번만 호출되어야 함
        verify(waitingListService, times(1)).getWaitingListSize(courseId);
        verify(sseWaitingEventPublisher, times(2)).publish(eq(courseId), any(UUID.class), any());
    }
}
