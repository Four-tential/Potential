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

    /**
     * 락 안에서 트랜잭션을 포함할지 여부
     *
     * true (기본값):
     *   AopInTransaction.proceed() 를 통해 REQUIRES_NEW 트랜잭션 안에서 실행
     *   일반 결제/환불 흐름에서 사용 (DB 변경이 트랜잭션으로 보호되어야 하는 경우)
     *
     * false:
     *   joinPoint.proceed() 를 직접 호출 - 트랜잭션 없이 락만 적용
     *   Batch Job 에서 PortOne API 호출이 포함된 경우 사용
     *   이유: 트랜잭션이 PortOne API 호출까지 묶이면 외부 API 대기 중 DB 커넥션을 점유함
     */
    boolean withTransaction() default true;
}
