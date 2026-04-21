package four_tential.potential.infra.redis.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DistributedLock {
    /**
     * Redis 분산 락 키 (SpEL 표현식 지원)
     * 예: "#memberId", "#p0", "#order.courseId"
     */
    String key();

    /**
     * 락 획득 대기 시간 (기본값: 5초)
     */
    long waitTime() default 5L;

    /**
     * 락 유지 시간
     * - 양수: 고정 leaseTime 사용 (기본값: 10초)
     * - -1: Redisson watchdog 활성화 — 스레드가 살아있는 동안 자동 갱신
     *   (작업 시간이 불확실한 경우에만 사용, 평상시에는 고정 leaseTime 권장)
     */
    long leaseTime() default 10L;

    TimeUnit timeUnit() default TimeUnit.SECONDS;
}
