package four_tential.potential.application.order.event;

import four_tential.potential.application.order.OrderConstants;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.TimeUnit;

import static four_tential.potential.application.order.OrderConstants.ORDER_EXPIRATION_QUEUE;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final RedissonClient redissonClient;
    private RDelayedQueue<String> delayedQueue;

    @PostConstruct
    public void init() {
        RQueue<String> destinationQueue = redissonClient.getQueue(ORDER_EXPIRATION_QUEUE);
        this.delayedQueue = redissonClient.getDelayedQueue(destinationQueue);
        log.info("주문 만료 RDelayedQueue 초기화 완료");
    }

    @PreDestroy
    public void destroy() {
        if (delayedQueue != null) {
            delayedQueue.destroy();
            log.info("주문 만료 RDelayedQueue 자원 정리 완료");
        }
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        try {
            delayedQueue.offer(event.orderId().toString(), OrderConstants.PENDING_ORDER_EXPIRATION_MINUTES, TimeUnit.MINUTES);
            log.info("주문 만료 딜레이 큐 등록 완료: orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("주문 만료 딜레이 큐 등록 중 예외 발생: orderId={}", event.orderId(), e);
        }
    }
}
