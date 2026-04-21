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
public class OrderConfirmationScheduler {

    private final OrderService orderService;
    private final RedissonClient redissonClient;

    private static final String LOCK_KEY = "lock:order:confirmation";
    private static final int BATCH_SIZE = 100;
    private static final int MAX_TOTAL_PROCESS = 5000;

    /**
     * 매일 00:10에 실행: 환불 기간이 지난 PAID 주문을 CONFIRMED로 변경
     * 분산 환경에서 중복 실행을 방지하기 위해 Redisson 분산 락을 사용합니다.
     */
    @Scheduled(cron = "0 10 0 * * *")
    public void confirmOrders() {
        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            boolean isLocked = lock.tryLock(0, 10, TimeUnit.MINUTES);

            if (isLocked) {
                log.info("결제 완료 주문 자동 확정 스케줄러 시작");

                LocalDateTime now = LocalDateTime.now();
                int totalProcessed = 0;

                while (totalProcessed < MAX_TOTAL_PROCESS) {
                    OrderBatchResult result = orderService.processConfirmedBatch(now, BATCH_SIZE);

                    if (result.fetchedCount() == 0 || result.successCount() == 0) {
                        if (result.fetchedCount() > 0) {
                            log.warn("배치 내 모든 주문 확정 처리가 실패했습니다. 무한 루프 방지를 위해 이번 턴을 종료합니다.");
                        }
                        break;
                    }

                    totalProcessed += result.successCount();
                    log.info("확정 주문 배치 처리 완료 (성공 {}건 / 조회 {}건, 누적 성공 {}건)", 
                            result.successCount(), result.fetchedCount(), totalProcessed);
                }

                if (totalProcessed > 0) {
                    log.info("결제 완료 주문 자동 확정 스케줄러 종료 (총 {}건 처리)", totalProcessed);
                }
            }
        } catch (InterruptedException e) {
            log.error("주문 확정 스케줄러 실행 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
