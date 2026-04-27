package four_tential.potential.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderInventoryReconciliationScheduler {

    private final OrderService orderService;
    private final WaitingListService waitingListService;
    private final RedissonClient redissonClient;

    private static final String LOCK_KEY = "lock:order:reconciliation";

    /**
     * 30분마다 실행: 서버 다운 등 예기치 못한 장애로 인한 Redis 재고 유실을 방어하기 위해,
     * DB의 유효 주문을 기준으로 Redis의 잔여석을 주기적으로 동기화(Self-Healing)합니다.
     */
    @Scheduled(cron = "0 0/30 * * * *")
    public void reconcileAllInventories() {
        RLock lock = redissonClient.getLock(LOCK_KEY);
        try {
            if (lock.tryLock(0, 10, TimeUnit.MINUTES)) {
                log.info("정기 재고 정합성 복구 배치 시작");
                
                List<UUID> courseIds = waitingListService.getActiveCourseIds();
                int successCount = 0;

                for (UUID courseId : courseIds) {
                    try {
                        orderService.reconcileInventory(courseId);
                        successCount++;
                    } catch (Exception e) {
                        log.error("코스 재고 복구 중 예외 발생: courseId={}, reason={}", courseId, e.getMessage());
                    }
                }

                log.info("정기 재고 정합성 복구 배치 종료 (총 {}건 중 {}건 성공)", courseIds.size(), successCount);
            }
        } catch (InterruptedException e) {
            log.error("재고 정합성 복구 배치 실행 중 인터럽트 발생", e);
            Thread.currentThread().interrupt();
        } finally {
            if (lock.isHeldByCurrentThread()) {
                lock.unlock();
            }
        }
    }
}
