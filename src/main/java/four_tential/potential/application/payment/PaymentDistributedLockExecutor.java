package four_tential.potential.application.payment;

import four_tential.potential.infra.redis.RedisConstants;
import four_tential.potential.infra.redis.annotation.DistributedLock;
import org.springframework.stereotype.Component;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * PaymentFacade 내부 private method에 @DistributedLock을 붙이면
 * self-invocation 문제로 AOP가 적용되지 않을 수 있어 별도 Bean으로 분리
 */
@Component
public class PaymentDistributedLockExecutor {

    // 사용자가 결제 생성 요청을 중복 전송해도 한 번에 하나만 처리되게 함
    @DistributedLock(key = "#p0 == null ? '' : '" + RedisConstants.PAYMENT_ORDER_LOCK_PREFIX + "' + #p0")
    public <T> T executeWithOrderLock(UUID orderId, Supplier<T> action) {
        return action.get();
    }

    // 같은 courseId의 confirmCount 증가가 동시에 일어나는 것 방지
    @DistributedLock(key = "#p0 == null ? '' : '" + RedisConstants.PAYMENT_COURSE_LOCK_PREFIX + "' + #p0")
    public <T> T executeWithCourseLock(UUID courseId, Supplier<T> action) {
        return action.get();
    }

    // 같은 PortOne 결제 식별자(pgKey)에 대한 웹훅 중복 처리 방지
    // 같은 결제에 대해 Paid/Failed 같은 이벤트가 동시에 처리되는 상황 방어
    @DistributedLock(key = "#p0 == null || #p0.isBlank() ? '' : '" + RedisConstants.PAYMENT_PG_LOCK_PREFIX + "' + #p0")
    public <T> T executeWithPgKeyLock(String pgKey, Supplier<T> action) {
        return action.get();
    }
}
