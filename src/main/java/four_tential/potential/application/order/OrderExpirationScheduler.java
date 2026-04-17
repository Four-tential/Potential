package four_tential.potential.application.order;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderExpirationScheduler {

    private final OrderService orderService;
    private final RedissonClient redissonClient;

    private static final String LOCK_KEY = "lock:order:expiration";

    /**
     * 1분마다 실행: 만료 시간이 지난 PENDING 주문을 EXPIRED로 변경
     * 분산 환경에서 중복 실행을 방지하기 위해 Redisson 분산 락을 사용
     */
    @Scheduled(cron = "0 * * * * *")
    public void expireOrders() {
        RLock lock = redissonClient.getLock(LOCK_KEY);

        try {
            // waitTime을 0으로 설정하여 락을 획득하지 못하면 즉시 포기 (다른 인스턴스가 이미 실행 중)
            // leaseTime은 작업 시간을 충분히 고려하여 50초로 설정 (스케줄 간격 1분보다 작게)
            boolean isLocked = lock.tryLock(0, 50, TimeUnit.SECONDS);

            if (isLocked) {
                log.info("만료된 주문 자동 만료 스케줄러 실행 (락 획득 성공)");
                orderService.processExpiredOrders();
            } else {
                log.debug("만료된 주문 자동 만료 스케줄러 실행 스킵 (다른 인스턴스에서 이미 실행 중)");
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
