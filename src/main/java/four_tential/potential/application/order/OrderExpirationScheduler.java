package four_tential.potential.application.order;

import four_tential.potential.application.order.OrderService.OrderBatchResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderService orderService;
    private final RedissonClient redissonClient;

    private static final String LOCK_KEY = "lock:order:expiration";
    private static final int BATCH_SIZE = 100;
    private static final int MAX_TOTAL_PROCESS = 1000;

    /**
     * 1분마다 실행: 만료 시간이 지난 PENDING 주문을 EXPIRED로 변경
     * 분산 환경에서 중복 실행을 방지하기 위해 Redisson 분산 락을 사용합니다.
     */
    @Scheduled(cron = "0 * * * * *")
    public void expireOrders() {
        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            boolean isLocked = lock.tryLock(0, 50, TimeUnit.SECONDS);

            if (isLocked) {
                log.info("만료된 주문 자동 만료 스케줄러 시작");

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
                    log.info("만료된 주문 자동 만료 스케줄러 종료 (총 {}건 처리)", totalProcessed);
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

