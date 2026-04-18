package four_tential.potential.application.order;

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
        
        // 첫 번째 호출에서 10건 처리, 두 번째에서 0건 처리 (종료 조건)
        given(orderService.processExpiredBatch(any(LocalDateTime.class), anyInt()))
                .willReturn(10)
                .willReturn(0);

        // when
        scheduler.expireOrders();

        // then
        verify(lock).tryLock(eq(0L), eq(50L), eq(TimeUnit.SECONDS));
        verify(orderService, times(2)).processExpiredBatch(any(LocalDateTime.class), anyInt());
        verify(lock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 시 주문 처리를 수행하지 않는다")
    void expireOrders_skip_when_lock_not_acquired() throws InterruptedException {
        // given
        given(redissonClient.getLock(anyString())).willReturn(lock);
        given(lock.tryLock(eq(0L), eq(50L), eq(TimeUnit.SECONDS))).willReturn(false);

        // when
        scheduler.expireOrders();

        // then
        verify(orderService, never()).processExpiredBatch(any(), anyInt());
        verify(lock, never()).unlock();
    }
}
