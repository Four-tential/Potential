package four_tential.potential.application.order;

import four_tential.potential.application.order.OrderService.OrderBatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

    @Mock private OrderService orderService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;

    @InjectMocks private OrderExpirationScheduler scheduler;

    @Test
    @DisplayName("락 획득 성공 시 만료 주문 처리를 수행한다")
    void expireOrders_success_when_lock_acquired() throws InterruptedException {
        // given
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(eq(0L), eq(50L), eq(TimeUnit.SECONDS))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        
        // 첫 번째 호출에서 10건 조회/10건 성공, 두 번째에서 0건 조회 (종료 조건)
        given(orderService.processExpiredBatch(any(LocalDateTime.class), anyInt()))
                .willReturn(new OrderBatchResult(10, 10))
                .willReturn(new OrderBatchResult(0, 0));

        // when
        scheduler.expireOrders();

        // then
        verify(lock).tryLock(eq(0L), eq(50L), eq(TimeUnit.SECONDS));
        verify(orderService, times(2)).processExpiredBatch(any(LocalDateTime.class), anyInt());
        verify(lock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 시 주문 처리를 수행하지 않고 언락을 시도하지 않는다")
    void expireOrders_skip_when_lock_not_acquired() throws InterruptedException {
        // given
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(eq(0L), eq(50L), eq(TimeUnit.SECONDS))).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        // when
        scheduler.expireOrders();

        // then
        verify(orderService, never()).processExpiredBatch(any(), anyInt());
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("배치 처리 중 예외가 발생하더라도 락을 해제한다")
    void expireOrders_unlock_even_if_exception_occurs() throws InterruptedException {
        // given
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        
        // 처리 중 예외 발생 시뮬레이션
        given(orderService.processExpiredBatch(any(), anyInt()))
                .willThrow(new RuntimeException("Batch processing failed"));

        // when
        // @Scheduled 메서드이므로 예외가 내부에서 로깅되거나 전파될 텐데, 현재 코드는 catch하지 않으므로 전파됨
        assertThatThrownBy(() -> scheduler.expireOrders())
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Batch processing failed");

        // then
        verify(lock).unlock();
    }

    @Test
    @DisplayName("현재 스레드가 락을 보유하고 있지 않으면 언락을 호출하지 않는다")
    void expireOrders_not_unlock_if_not_held_by_current_thread() throws InterruptedException {
        // given
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        
        // 락 획득 후 로직 수행을 위해 빈 배치 결과 설정
        given(orderService.processExpiredBatch(any(), anyInt()))
                .willReturn(new OrderBatchResult(0, 0));

        // 로직 수행 후, 어떤 이유로든(예: 타임아웃 등) 현재 스레드가 락을 잃었을 때를 시뮬레이션
        given(lock.isHeldByCurrentThread()).willReturn(false);

        // when
        scheduler.expireOrders();

        // then
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("배치 내 모든 주문 처리가 실패하면 무한 루프 방지를 위해 이번 턴을 종료한다")
    void expireOrders_stop_when_all_items_fail_in_batch() throws InterruptedException {
        // given
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        // 조회는 10건 되었으나, 처리는 0건 성공한 상황 (예: 모두 낙관적 락 충돌)
        given(orderService.processExpiredBatch(any(), anyInt()))
                .willReturn(new OrderBatchResult(10, 0));

        // when
        scheduler.expireOrders();

        // then
        // 단 한 번만 호출되고 루프를 탈출해야 함 (무한 루프 방지)
        verify(orderService, times(1)).processExpiredBatch(any(), anyInt());
        verify(lock).unlock();
    }
}
