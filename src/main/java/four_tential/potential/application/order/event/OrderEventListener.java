package four_tential.potential.application.order.event;

import four_tential.potential.application.order.OrderConstants;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RDelayedQueue;
import org.redisson.api.RQueue;
import org.redisson.api.RedissonClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.concurrent.TimeUnit;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderEventListener {

    private final RedissonClient redissonClient;
    private static final String QUEUE_NAME = "queue:order:expiration";

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void handleOrderCreatedEvent(OrderCreatedEvent event) {
        try {
            RQueue<String> queue = redissonClient.getQueue(QUEUE_NAME);
            RDelayedQueue<String> delayedQueue = redissonClient.getDelayedQueue(queue);
            delayedQueue.offer(event.orderId().toString(), OrderConstants.PENDING_ORDER_EXPIRATION_MINUTES, TimeUnit.MINUTES);
            
            log.info("주문 만료 딜레이 큐 등록 완료: orderId={}", event.orderId());
        } catch (Exception e) {
            log.error("주문 만료 딜레이 큐 등록 중 예외 발생: orderId={}", event.orderId(), e);
        }
    }
}
