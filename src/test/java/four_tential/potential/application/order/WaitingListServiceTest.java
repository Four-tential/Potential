package four_tential.potential.application.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.*;

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

    @InjectMocks private WaitingListService waitingListService;

    private final UUID courseId = UUID.randomUUID();
    private final UUID memberId = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        lenient().doReturn(occupancyBucket).when(redissonClient).getBucket(anyString());
        lenient().doReturn(waitingListSet).when(redissonClient).getScoredSortedSet(anyString());
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
    @DisplayName("이미 점유 중이면 예외가 발생한다")
    void tryOccupyingSeat_throws_duplicate() {
        // given
        given(occupancyBucket.isExists()).willReturn(true);

        // when & then
        assertThatThrownBy(() -> waitingListService.tryOccupyingSeat(courseId, memberId, 1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining(OrderExceptionEnum.ERR_DUPLICATE_ORDER.getMessage());
    }

    @Test
    @DisplayName("점유된 잔여석을 수량에 맞춰 성공적으로 롤백한다")
    void rollbackOccupiedSeat_success() {
        // given
        given(occupancyBucket.isExists()).willReturn(true);
        given(occupancyBucket.get()).willReturn("3");

        // when
        waitingListService.rollbackOccupiedSeat(courseId, memberId);

        // then
        verify(occupancyBucket).delete();
        verify(capacityAtomic).addAndGet(3);
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
}
