package four_tential.potential.application.order;

import four_tential.potential.application.order.OrderService.OrderBatchResult;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.redisson.api.RBlockingQueue;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static four_tential.potential.application.order.OrderConstants.ORDER_EXPIRATION_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderService orderService;
    private final RedissonClient redissonClient;
    private ExecutorService executorService;

    private static final String LOCK_KEY = "lock:order:expiration";
    private static final int BATCH_SIZE = 100;
    private static final int MAX_TOTAL_PROCESS = 1000;

    @PostConstruct
    public void initDelayedQueueWorker() {
        RBlockingQueue<String> queue = redissonClient.getBlockingQueue(ORDER_EXPIRATION_QUEUE);
        
        // 워커 스레드 초기화
        executorService = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r);
            thread.setName("order-expiration-worker");
            return thread;
        });

        executorService.submit(() -> {
            log.info("주문 만료 처리 워커 스레드 시작 (Redis Delayed Queue)");
            if (queue == null) {
                log.warn("RBlockingQueue가 null입니다. (Mock 환경일 수 있음). 워커 스레드를 종료합니다.");
                return;
            }
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // 큐에 항목이 들어올 때까지 블로킹 대기 (10분 만료 시 DelayedQueue에서 이쪽으로 이동됨)
                    String orderIdStr = queue.take();
                    UUID orderId = UUID.fromString(orderIdStr);
                    
                    log.info("Delayed Queue에서 주문 만료 이벤트 수신: orderId={}", orderId);
                    orderService.expireOrderInNewTransaction(orderId);
                } catch (InterruptedException e) {
                    log.info("주문 만료 처리 워커 스레드 종료 요청됨");
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    log.error("주문 만료 큐 소비 중 예외 발생", e);
                    // 예외가 발생하더라도 워커 스레드가 죽지 않도록 잠시 대기
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }
        });
    }

    @PreDestroy
    public void destroyWorker() {
        if (executorService != null && !executorService.isShutdown()) {
            log.info("주문 만료 워커 스레드 종료 절차 시작 (Graceful Shutdown)");
            executorService.shutdown();
            try {
                if (!executorService.awaitTermination(5, TimeUnit.SECONDS)) {
                    log.warn("주문 만료 워커가 5초 내에 종료되지 않아 강제 종료를 시도합니다");
                    executorService.shutdownNow();
                }
            } catch (InterruptedException e) {
                log.error("주문 만료 워커 종료 대기 중 인터럽트 발생");
                Thread.currentThread().interrupt();
                executorService.shutdownNow();
            }
        }
    }

    /**
     * 30분마다 실행: Delayed Queue 유실 등 예외 상황을 대비한 보조 스케줄러 (Fallback)
     */
    @Scheduled(cron = "0 0/30 * * * *")
    @SchedulerLock(name = "orderExpirationScheduler", lockAtMostFor = "20m", lockAtLeastFor = "5m")
    public void expireOrders() {
        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            boolean isLocked = lock.tryLock(0, 50, TimeUnit.SECONDS);

            if (isLocked) {
                LocalDateTime now = LocalDateTime.now();
                int totalProcessed = 0;

                while (totalProcessed < MAX_TOTAL_PROCESS) {
                    // 각 배치는 개별 트랜잭션으로 처리됨
                    OrderBatchResult result = orderService.processExpiredBatch(now, BATCH_SIZE);

                    if (result.fetchedCount() == 0) {
                        break;
                    }

                    totalProcessed += result.successCount();
                    log.info("만료 주문 배치 처리 완료 (성공 {}건 / 조회 {}건, 누적 성공 {}건)", 
                            result.successCount(), result.fetchedCount(), totalProcessed);

                    if (result.successCount() == 0) {
                        log.warn("배치 내 모든 주문 만료 처리가 실패했습니다. 무한 루프 방지를 위해 이번 턴을 종료합니다.");
                        break;
                    }
                }

                if (totalProcessed > 0) {
                    log.info("만료된 주문 자동 만료 보조 스케줄러 종료 (총 {}건 처리)", totalProcessed);
                }
            }
        } catch (InterruptedException e) {
            log.error("주문 만료 스케줄러 실행 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
