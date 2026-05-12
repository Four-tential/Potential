package four_tential.potential.application.order;

import four_tential.potential.application.order.OrderService.OrderBatchResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderExpirationSchedulerTest {

    @Mock private OrderService orderService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock lock;
    @Mock private RBlockingQueue<String> queue;

    @InjectMocks private OrderExpirationScheduler scheduler;

    @Test
    @DisplayName("워커 초기화 시 큐에 항목이 들어오면 만료 처리를 수행한다")
    void initDelayedQueueWorker_process_when_item_taken() throws InterruptedException {
        // given
        UUID testOrderId = UUID.randomUUID();
        given(redissonClient.<String>getBlockingQueue(anyString())).willReturn(queue);
        
        // 첫 번째 take()에서는 ID 반환, 두 번째에서는 InterruptedException 발생시켜 루프 종료
        given(queue.take())
                .willReturn(testOrderId.toString())
                .willThrow(new InterruptedException());

        // when
        scheduler.initDelayedQueueWorker();
        
        // 워커 스레드가 실행될 시간을 충분히 줌
        Thread.sleep(100);

        // then
        verify(orderService, times(1)).expireOrderInNewTransaction(testOrderId);
        
        // cleanup
        scheduler.destroyWorker();
    }
    
    @Test
    @DisplayName("워커 초기화 시 큐가 null이면 워커 스레드를 종료한다")
    void initDelayedQueueWorker_exit_when_queue_is_null() throws InterruptedException {
        // given
        given(redissonClient.<String>getBlockingQueue(anyString())).willReturn(null);

        // when
        scheduler.initDelayedQueueWorker();
        
        // 워커 스레드가 실행될 시간을 줌
        Thread.sleep(100);

        // then
        // null 체크로 인해 queue.take()가 호출되지 않아야 함
        verify(queue, never()).take();
        
        // cleanup
        scheduler.destroyWorker();
    }
    
    @Test
    @DisplayName("워커 처리 중 예외가 발생하더라도 스레드가 죽지 않고 다시 큐를 대기한다")
    void initDelayedQueueWorker_continue_when_exception_occurs() throws InterruptedException {
        // given
        UUID testOrderId1 = UUID.randomUUID();
        UUID testOrderId2 = UUID.randomUUID();
        given(redissonClient.<String>getBlockingQueue(anyString())).willReturn(queue);
        
        // 1: 정상 반환 (그러나 처리 중 예외 발생 가정), 2: 정상 반환, 3: 인터럽트(종료)
        given(queue.take())
                .willReturn(testOrderId1.toString())
                .willReturn(testOrderId2.toString())
                .willThrow(new InterruptedException());
                
        // 첫 번째 ID 처리 시 예외 발생시키기
        given(orderService.expireOrderInNewTransaction(testOrderId1))
                .willThrow(new RuntimeException("DB Error"));
        // 두 번째 ID 처리는 성공
        given(orderService.expireOrderInNewTransaction(testOrderId2))
                .willReturn(true);

        // when
        scheduler.initDelayedQueueWorker();
        
        // 예외 발생 후 sleep(1000)이 있으므로 충분히 기다림
        Thread.sleep(1200);

        // then
        verify(orderService, times(1)).expireOrderInNewTransaction(testOrderId1);
        verify(orderService, times(1)).expireOrderInNewTransaction(testOrderId2);
        
        // cleanup
        scheduler.destroyWorker();
    }

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

        given(orderService.processExpiredBatch(any(LocalDateTime.class), anyInt()))
                .willThrow(new RuntimeException("DB Connection Error"));

        // when
        try {
            scheduler.expireOrders();
        } catch (RuntimeException e) {
            // expected
        }

        // then
        verify(lock).unlock();
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
