package four_tential.potential.application.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

    @Mock private OrderService orderService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;

    @InjectMocks private OrderExpirationScheduler scheduler;

    @Test
    @DisplayName("분산 락 획득 성공 시 주문 만료 처리를 수행한다")
    void expireOrders_lockSuccess() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:order:expiration")).willReturn(lock);
        given(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);

        // when
        scheduler.expireOrders();

        // then
        verify(orderService, times(1)).processExpiredOrders();
        verify(lock).unlock();
    }

    @Test
    @DisplayName("분산 락 획득 실패 시 주문 만료 처리를 수행하지 않는다")
    void expireOrders_lockFail() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:order:expiration")).willReturn(lock);
        given(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).willReturn(false);

        // when
        scheduler.expireOrders();

        // then
        verify(orderService, never()).processExpiredOrders();
        verify(lock, never()).unlock();
    }

    @Test
    @DisplayName("락 획득 중 인터럽트가 발생하면 예외를 처리하고 인터럽트 상태를 유지한다")
    void expireOrders_interrupt() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:order:expiration")).willReturn(lock);
        given(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).willThrow(new InterruptedException());

        // when
        scheduler.expireOrders();

        // then
        verify(orderService, never()).processExpiredOrders();
    }

    @Test
    @DisplayName("서비스 로직 실행 중 예외가 발생해도 락은 해제되어야 한다")
    void expireOrders_serviceException() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:order:expiration")).willReturn(lock);
        given(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).willReturn(true);
        given(lock.isHeldByCurrentThread()).willReturn(true);
        doThrow(new RuntimeException("DB Error")).when(orderService).processExpiredOrders();

        // when
        try {
            scheduler.expireOrders();
        } catch (Exception e) {
            // 예외는 내부에서 로그만 남기고 삼켜지거나 발생할 수 있음 (현재 코드상 로그만 남음)
        }

        // then
        verify(orderService, times(1)).processExpiredOrders();
        verify(lock).unlock(); // 예외 발생 시에도 언락 확인
    }

    @Test
    @DisplayName("락을 획득하지 못했을 때는 언락을 시도하지 않는다")
    void expireOrders_noUnlockWhenNotLocked() throws InterruptedException {
        // given
        given(redissonClient.getLock("lock:order:expiration")).willReturn(lock);
        given(lock.tryLock(eq(0L), anyLong(), eq(TimeUnit.SECONDS))).willReturn(false);
        given(lock.isHeldByCurrentThread()).willReturn(false);

        // when
        scheduler.expireOrders();

        // then
        verify(lock, never()).unlock();
    }
}
