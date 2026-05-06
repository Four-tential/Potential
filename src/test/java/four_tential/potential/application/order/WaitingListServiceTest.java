package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.infra.redis.RedisConstants;
import four_tential.potential.infra.sse.SseWaitingEventPublisher;
import four_tential.potential.infra.sse.SseWaitingRoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitingListServiceTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RScript rScript;
    @Mock private RBucket<String> occupancyBucket;
    @Mock private RBucket<String> countBucket;
    @Mock private RScoredSortedSet<String> waitingListSet;
    @Mock private RAtomicLong capacityAtomic;
    @Mock private SseWaitingEventPublisher sseWaitingEventPublisher;
    @Mock private SseWaitingRoomRepository sseWaitingRoomRepository;

    @InjectMocks private WaitingListService waitingListService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().when(redissonClient.getScript(StringCodec.INSTANCE)).thenReturn(rScript);
        lenient().when(redissonClient.getAtomicLong(anyString())).thenReturn(capacityAtomic);
        lenient().doReturn(waitingListSet).when(redissonClient).getScoredSortedSet(anyString(), eq(StringCodec.INSTANCE));
        lenient().doReturn(occupancyBucket).when(redissonClient).getBucket(startsWith(RedisConstants.USER_COURSE_OCCUPANCY_PREFIX), eq(StringCodec.INSTANCE));
        lenient().doReturn(countBucket).when(redissonClient).getBucket(startsWith(RedisConstants.WAITING_ORDER_COUNT_PREFIX), eq(StringCodec.INSTANCE));
    }

    @Test
    @DisplayName("잔여석이 요청 수량보다 많고 대기열이 비어있으면 점유에 성공한다")
    void tryOccupyingSeat_success() {
        // given
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any(), any(), any(), any()))
                .thenReturn("1");

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 2);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("승격된 유저(PROMOTED)가 주문을 시도하면 재고 차감 후 점유에 성공한다")
    void tryOccupyingSeat_promoted_success() {
        // given
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any(), any(), any(), any()))
                .thenReturn("1");

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("점유 시 수량이 0 이하이면 예외가 발생한다")
    void tryOccupyingSeat_fail_invalidCount() {
        // when & then
        assertThatThrownBy(() -> waitingListService.tryOccupyingSeat(courseId, memberId, 0))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT.getMessage());
    }

    @Test
    @DisplayName("이미 점유 중이거나 대기 중인 유저가 재요청하면 중복 에러가 발생한다")
    void tryOccupyingSeat_fail_duplicate() {
        // given
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any(), any(), any(), any()))
                .thenReturn("-2");

        // when & then
        assertThatThrownBy(() -> waitingListService.tryOccupyingSeat(courseId, memberId, 1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_DUPLICATE_ORDER.getMessage());
    }

    @Test
    @DisplayName("대기열이 존재하거나 잔여석이 부족하면 점유에 실패하고 false를 반환한다")
    void tryOccupyingSeat_fail_and_return_false() {
        // given
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any(), any(), any(), any()))
                .thenReturn("0");

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("점유된 잔여석을 성공적으로 롤백하고 다음 대기자를 승격시킨다")
    void rollbackOccupiedSeat_success() {
        // given
        given(occupancyBucket.get()).willReturn("3");
        UUID nextId = UUID.randomUUID();
        // promoteNextInWaitingList (6 args)
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any()))
                .thenReturn(nextId.toString());
        given(sseWaitingRoomRepository.find(courseId, nextId)).willReturn(Optional.of(mock(SseEmitter.class)));

        // when
        waitingListService.rollbackOccupiedSeat(courseId, memberId);

        // then
        verify(occupancyBucket).delete();
        verify(capacityAtomic).addAndGet(3);
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextId), any());
    }

    @Test
    @DisplayName("승격된 유저가 주문을 시도했으나 재고가 부족해지면(과승격 방지) 다음 대기자를 승격시킨다")
    void tryOccupyingSeat_promoted_fail_and_promote_next() {
        // given
        UUID nextId = UUID.randomUUID();
        // tryOccupyingSeat (9 args) returns next member ID
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any(), any(), any(), any()))
                .thenReturn(nextId.toString());
        given(sseWaitingRoomRepository.find(courseId, nextId)).willReturn(Optional.of(mock(SseEmitter.class)));

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isFalse();
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextId), any());
    }

    @Test
    @DisplayName("승격 시 대상자의 SSE 연결이 없으면 다음 대기자를 승격시킨다")
    void promoteNextInWaitingList_skip_when_sse_disconnected() {
        // given
        UUID disconnectedMemberId = UUID.randomUUID();
        UUID nextMemberId = UUID.randomUUID();
        
        // promoteNextInWaitingList (6 args)
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any()))
                .thenReturn(disconnectedMemberId.toString(), nextMemberId.toString(), null);
        
        given(sseWaitingRoomRepository.find(courseId, disconnectedMemberId)).willReturn(Optional.empty());
        given(sseWaitingRoomRepository.find(courseId, nextMemberId)).willReturn(Optional.of(mock(SseEmitter.class)));

        // when (recoverCapacity를 통해 간접 호출)
        waitingListService.recoverCapacity(courseId, memberId, 1);

        // then
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextMemberId), any());
        verify(sseWaitingEventPublisher, never()).publish(eq(courseId), eq(disconnectedMemberId), any());
    }

    @Test
    @DisplayName("대기열에 성공적으로 추가된다")
    void addToWaitingList_success() {
        // given
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.LONG), anyList(), any(), any(), any(), any()))
                .thenReturn(1L);

        // when
        waitingListService.addToWaitingList(courseId, memberId, 1);

        // then
        verify(rScript).eval(eq(RScript.Mode.READ_WRITE), anyString(), eq(RScript.ReturnType.LONG), anyList(), 
                eq(memberId.toString()), eq("1"), anyString(), anyString());
    }

    @Test
    @DisplayName("취소 시 잔여석 수량을 복구하고 점유 정보를 삭제한다")
    void recoverCapacity_success() {
        // given
        // promoteNextInWaitingList (6 args) returns null (no one to promote)
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any()))
                .thenReturn(null);

        // when
        waitingListService.recoverCapacity(courseId, memberId, 2);

        // then
        verify(capacityAtomic).addAndGet(2);
        verify(occupancyBucket).delete();
    }

    @Test
    @DisplayName("대기열 순번을 조회하면 1부터 시작하는 순번을 반환한다")
    void getWaitingRank_success() {
        // given
        given(waitingListSet.rank(memberId.toString())).willReturn(0);

        // when
        Long rank = waitingListService.getWaitingRank(courseId, memberId);

        // then
        assertThat(rank).isEqualTo(1L);
    }

    @Test
    @DisplayName("대기열 총 인원을 성공적으로 조회한다")
    void getWaitingListSize_success() {
        // given
        given(waitingListSet.size()).willReturn(10);

        // when
        int size = waitingListService.getWaitingListSize(courseId);

        // then
        assertThat(size).isEqualTo(10);
    }

    @Test
    @DisplayName("잔여석 수치를 강제로 업데이트하고 수치가 증가하면 대기열 승격을 시도한다")
    void updateCapacity_increase_triggers_promotion() {
        // given
        given(capacityAtomic.get()).willReturn(5L);
        UUID nextId = UUID.randomUUID();
        lenient().when(rScript.eval(any(), anyString(), eq(RScript.ReturnType.VALUE), anyList(), any(), any()))
                .thenReturn(nextId.toString());
        given(sseWaitingRoomRepository.find(courseId, nextId)).willReturn(Optional.of(mock(SseEmitter.class)));

        // when
        waitingListService.updateCapacity(courseId, 10L);

        // then
        verify(capacityAtomic).set(10L);
        verify(sseWaitingEventPublisher).publish(eq(courseId), eq(nextId), any());
    }

    @Test
    @DisplayName("잔여석 점유 확정 시 점유 정보가 삭제된다")
    void completeOccupyingSeat_success() {
        // given
        given(occupancyBucket.isExists()).willReturn(true);

        // when
        waitingListService.completeOccupyingSeat(courseId, memberId);

        // then
        verify(occupancyBucket).delete();
    }

    @Test
    @DisplayName("현재 활성화된 코스 ID 목록을 조회한다")
    void getActiveCourseIds_success() {
        // given
        RKeys rKeys = mock(RKeys.class);
        given(redissonClient.getKeys()).willReturn(rKeys);
        String courseKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        given(rKeys.getKeysByPattern(anyString())).willReturn(List.of(courseKey));

        // when
        var result = waitingListService.getActiveCourseIds();

        // then
        assertThat(result).containsExactly(courseId);
    }
}
