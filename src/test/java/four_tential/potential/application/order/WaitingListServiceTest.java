package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import four_tential.potential.infra.sse.SseWaitingEventPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;
import org.redisson.client.codec.StringCodec;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WaitingListServiceTest {

    @Mock private RedissonClient redissonClient;
    @Mock private RBucket<String> occupancyBucket;
    @Mock private RScoredSortedSet<String> waitingListSet;
    @Mock private RAtomicLong capacityAtomic;
    @Mock private SseWaitingEventPublisher sseWaitingEventPublisher;

    @InjectMocks private WaitingListService waitingListService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        // StringCodec을 사용하는 메서드 오버로딩에 대응
        lenient().doReturn(occupancyBucket).when(redissonClient).getBucket(anyString(), eq(StringCodec.INSTANCE));
        lenient().doReturn(waitingListSet).when(redissonClient).getScoredSortedSet(anyString(), eq(StringCodec.INSTANCE));
        lenient().when(redissonClient.getAtomicLong(anyString())).thenReturn(capacityAtomic);
    }

    @Test
    @DisplayName("잔여석이 요청 수량보다 많고 대기열이 비어있으면 점유에 성공한다")
    void tryOccupyingSeat_success() {
        // given
        int orderCount = 2;
        given(occupancyBucket.isExists()).willReturn(false);
        given(waitingListSet.contains(memberId.toString())).willReturn(false);
        given(waitingListSet.isEmpty()).willReturn(true); // 대기열이 비어있어야 함
        given(capacityAtomic.get()).willReturn(10L);

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, orderCount);

        // then
        assertThat(result).isTrue();
        verify(capacityAtomic).addAndGet(-orderCount);
    }

    @Test
    @DisplayName("승격된 유저(PROMOTED)가 주문을 시도하면 재고 차감 후 점유에 성공한다")
    void tryOccupyingSeat_promoted_success() {
        // given
        given(occupancyBucket.isExists()).willReturn(true);
        given(occupancyBucket.get()).willReturn(OrderConstants.TOKEN_PROMOTED);
        given(capacityAtomic.get()).willReturn(5L);

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isTrue();
        verify(capacityAtomic).addAndGet(-1);
    }

    @Test
    @DisplayName("점유 시 수량이 0 이하이면 예외가 발생한다")
    void tryOccupyingSeat_fail_invalidCount() {
        // when & then
        assertThatThrownBy(() -> waitingListService.tryOccupyingSeat(courseId, memberId, 0))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT.getMessage());

        assertThatThrownBy(() -> waitingListService.tryOccupyingSeat(courseId, memberId, -1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_INVALID_ORDER_COUNT.getMessage());
    }

    @Test
    @DisplayName("대기열에 사람이 있으면 잔여석이 있어도 점유에 실패하고 대기열로 유도한다")
    void tryOccupyingSeat_fail_due_to_existing_waiting_list() {
        // given
        given(occupancyBucket.isExists()).willReturn(false);
        given(waitingListSet.contains(memberId.toString())).willReturn(false);
        given(waitingListSet.isEmpty()).willReturn(false); // 대기열에 사람이 있음

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isFalse();
        verify(capacityAtomic, never()).get(); // 재고 확인까지 가지 않아야 함
    }

    @Test
    @DisplayName("잔여석이 없으면 점유에 실패한다")
    void tryOccupyingSeat_fail_no_capacity() {
        // given
        given(occupancyBucket.isExists()).willReturn(false);
        given(waitingListSet.contains(memberId.toString())).willReturn(false);
        given(waitingListSet.isEmpty()).willReturn(true);
        given(capacityAtomic.get()).willReturn(0L);

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isFalse();
        verify(capacityAtomic, never()).addAndGet(anyLong());
    }

    @Test
    @DisplayName("이미 점유 중인 유저가 재요청하면 true를 반환한다")
    void tryOccupyingSeat_already_occupied_returns_true() {
        // given
        given(occupancyBucket.isExists()).willReturn(true);
        given(occupancyBucket.get()).willReturn("1"); // 이미 1개 점유 중인 상태

        // when
        boolean result = waitingListService.tryOccupyingSeat(courseId, memberId, 1);

        // then
        assertThat(result).isTrue();
        verify(capacityAtomic, never()).addAndGet(anyLong()); // 재고를 중복으로 깎지 않아야 함
    }

    @Test
    @DisplayName("점유된 잔여석을 수량에 맞춰 성공적으로 롤백한다")
    void rollbackOccupiedSeat_success() {
        // given
        given(occupancyBucket.get()).willReturn("3");
        // 승격 로직을 위한 설정
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(1L);
        given(waitingListSet.pollFirst()).willReturn(UUID.randomUUID().toString());

        // when
        waitingListService.rollbackOccupiedSeat(courseId, memberId);

        // then
        verify(occupancyBucket).delete();
        verify(capacityAtomic).addAndGet(3);
        verify(sseWaitingEventPublisher).publish(eq(courseId), any(), any());
    }

    @Test
    @DisplayName("대기열에 성공적으로 추가된다")
    void addToWaitingList_success() {
        // given
        given(waitingListSet.contains(memberId.toString())).willReturn(false);
        given(waitingListSet.size()).willReturn(50);

        // when
        waitingListService.addToWaitingList(courseId, memberId);

        // then
        verify(waitingListSet).add(anyDouble(), eq(memberId.toString()));
    }

    @Test
    @DisplayName("취소 시 잔여석 수량을 복구하고 점유 정보를 삭제한다")
    void recoverCapacity_success() {
        // given
        int orderCount = 2;
        given(waitingListSet.isEmpty()).willReturn(false);
        given(capacityAtomic.get()).willReturn(1L);
        given(waitingListSet.pollFirst()).willReturn(UUID.randomUUID().toString());

        // when
        waitingListService.recoverCapacity(courseId, memberId, orderCount);

        // then
        verify(capacityAtomic).addAndGet(orderCount);
        verify(occupancyBucket).delete();
        verify(sseWaitingEventPublisher).publish(eq(courseId), any(), any());
    }

    @Test
    @DisplayName("대기열 순번을 조회하면 1부터 시작하는 순번을 반환한다")
    void getWaitingRank_success() {
        // given
        given(waitingListSet.rank(memberId.toString())).willReturn(0); // Redis rank는 0부터 시작

        // when
        Long rank = waitingListService.getWaitingRank(courseId, memberId);

        // then
        assertThat(rank).isEqualTo(1L);
    }

    @Test
    @DisplayName("대기열에 없으면 순번 조회 시 null을 반환한다")
    void getWaitingRank_null() {
        // given
        given(waitingListSet.rank(memberId.toString())).willReturn(null);

        // when
        Long rank = waitingListService.getWaitingRank(courseId, memberId);

        // then
        assertThat(rank).isNull();
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
    @DisplayName("대기열에서 성공적으로 이탈한다")
    void removeFromWaitingList_success() {
        // given
        given(waitingListSet.remove(memberId.toString())).willReturn(true);

        // when
        waitingListService.removeFromWaitingList(courseId, memberId);

        // then
        verify(waitingListSet).remove(memberId.toString());
    }
}
