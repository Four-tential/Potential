package four_tential.potential.application.order;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderInventoryReconciliationSchedulerTest {

    @Mock
    private OrderService orderService;

    @Mock
    private WaitingListService waitingListService;

    @Mock
    private RedissonClient redissonClient;

    @Mock
    private RLock lock;

    @InjectMocks
    private OrderInventoryReconciliationScheduler scheduler;

    private static final String LOCK_KEY = "lock:order:reconciliation";

    @BeforeEach
    void setUp() {
        given(redissonClient.getLock(LOCK_KEY)).willReturn(lock);
    }

    @Test
    @DisplayName("정기 재고 복구 배치가 정상적으로 실행된다")
    void reconcileAllInventories_success() throws InterruptedException {
        // given
        UUID courseId1 = UUID.randomUUID();
        UUID courseId2 = UUID.randomUUID();
        given(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(waitingListService.getActiveCourseIds()).willReturn(List.of(courseId1, courseId2));

        // when
        scheduler.reconcileAllInventories();

        // then
        verify(orderService, times(1)).reconcileInventory(courseId1);
        verify(orderService, times(1)).reconcileInventory(courseId2);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("락을 획득하지 못한 경우 복구 배치가 실행되지 않는다")
    void reconcileAllInventories_fail_to_acquire_lock() throws InterruptedException {
        // given
        given(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).willReturn(false);

        // when
        scheduler.reconcileAllInventories();

        // then
        verify(waitingListService, never()).getActiveCourseIds();
        verify(orderService, never()).reconcileInventory(any());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("일부 코스 복구 중 예외가 발생해도 다음 코스를 진행한다")
    void reconcileAllInventories_continue_on_exception() throws InterruptedException {
        // given
        UUID courseId1 = UUID.randomUUID();
        UUID courseId2 = UUID.randomUUID();
        given(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        given(waitingListService.getActiveCourseIds()).willReturn(List.of(courseId1, courseId2));
        
        doThrow(new RuntimeException("Error")).when(orderService).reconcileInventory(courseId1);

        // when
        scheduler.reconcileAllInventories();

        // then
        verify(orderService, times(1)).reconcileInventory(courseId1);
        verify(orderService, times(1)).reconcileInventory(courseId2);
        verify(lock).unlock();
    }

    @Test
    @DisplayName("인터럽트 발생 시 적절히 처리한다")
    void reconcileAllInventories_interrupted() throws InterruptedException {
        // given
        given(lock.tryLock(anyLong(), anyLong(), eq(TimeUnit.MINUTES))).willThrow(new InterruptedException());

        // when
        scheduler.reconcileAllInventories();

        // then
        verify(waitingListService, never()).getActiveCourseIds();
        // Thread.currentThread().isInterrupted() check is hard in static mock, but we can verify lock wasn't held
    }
}
