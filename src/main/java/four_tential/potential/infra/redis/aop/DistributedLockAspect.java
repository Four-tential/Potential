package four_tential.potential.infra.redis.aop;


import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.redis.annotation.DistributedLock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.Ordered;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

import java.lang.reflect.Method;

import static four_tential.potential.common.exception.domain.CommonExceptionEnum.ERR_DISTRIBUTED_LOCK_KEY_NULL;
import static four_tential.potential.common.exception.domain.CommonExceptionEnum.ERR_GET_DISTRIBUTED_LOCK_FAIL;

/**
 * 어노테이션이 붙은 메서드에 Redis 분산 락을 적용하는 Aspect
 *
 * 실행 순서 보장 ({@code @Order(HIGHEST_PRECEDENCE)})
 * 락 Aspect가 {@code @Transactional} Aspect보다 반드시 먼저 실행되어야 합니다
 * 올바른 순서: [락 획득] → [트랜잭션 시작] → [로직 실행] → [트랜잭션 커밋] → [락 해제]
 * 순서가 반대라면 락 해제 시점에 아직 커밋되지 않은 데이터를 다른 스레드가 읽을 수 있음
 *
 * 트랜잭션 위임 ({@link AopInTransaction})
 * 서비스 메서드의 {@code @Transactional}이 이 Aspect보다 먼저 감싸지지 않도록,
 * {@code AopInTransaction}(별도 빈)을 통해 {@code REQUIRES_NEW} 트랜잭션을 강제로 시작
 * 이렇게 하면 메서드 로직이 끝나고 트랜잭션이 커밋된 이후에야 finally 블록의 락 해제가 실행
 */
@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DistributedLockAspect {

    private final RedissonClient redissonClient;
    private final AopInTransaction aopInTransaction;

    // SpEL 표현식 파서 — @DistributedLock(key = "#memberId") 같은 키 표현식을 런타임에 평가
    private final ExpressionParser parser = new SpelExpressionParser();

    // 파라미터 이름 탐색기
    // LocalVariableTableParameterNameDiscoverer + StandardReflectionParameterNameDiscoverer 조합으로
    // MethodSignature.getParameterNames()보다 넓은 환경에서 파라미터 이름을 안정적으로 탐색
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 분산 락 적용 어드바이스
     * 포인트컷에 어노테이션 바인딩 방식(@annotation(distributedLock) + 파라미터)을 사용하지 않고 메서드 내부에서 직접 추출
     * 프록시 구조가 복잡한 경우 파라미터 바인딩 방식에서 간헐적으로 바인딩 오류가 발생하기 때문
     */
    @Around("@annotation(four_tential.potential.infra.redis.annotation.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();

        // 어노테이션 정보 직접 추출
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);

        Object[] argsArr = joinPoint.getArgs();
        String[] paramNames = nameDiscoverer.getParameterNames(method);

        // SpEL 평가 컨텍스트에 파라미터 바인딩
        StandardEvaluationContext context = new StandardEvaluationContext();

        // 1) 이름 기반 바인딩: #memberId, #courseId 등
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], argsArr[i]);
            }
        }

        // 2) 인덱스 기반 폴백 바인딩: 이름 탐색에 실패한 환경을 위해 #p0, #arg0 형태도 등록
        for (int i = 0; i < argsArr.length; i++) {
            context.setVariable("p" + i, argsArr[i]);
            context.setVariable("arg" + i, argsArr[i]);
        }

        // SpEL 표현식 평가로 실제 락 키 값 도출
        // 표현식 자체가 잘못 작성된 경우(SpellParseException 등) 커스텀 예외로 변환해 500 응답을 방지
        String evaluatedKey;
        try {
            evaluatedKey = parser.parseExpression(distributedLock.key()).getValue(context, String.class);
        } catch (Exception e) {
            log.error(">>> [LOCK ASPECT ERROR] SpEL Evaluation Failed: {}", e.getMessage());
            throw new ServiceErrorException(ERR_DISTRIBUTED_LOCK_KEY_NULL);
        }

        if (evaluatedKey == null || evaluatedKey.isBlank()) {
            throw new ServiceErrorException(ERR_DISTRIBUTED_LOCK_KEY_NULL);
        }

        // Redis 키에 네임스페이스 prefix를 붙여 다른 키와 충돌 방지
        // 예: courseId = "abc-123" → "dLock:abc-123"
        String key = "dLock:" + evaluatedKey;
        RLock rLock = redissonClient.getLock(key);

        // 락 획득 시도
        // leaseTime < 0 (관례상 -1): watchdog 모드 — 스레드가 살아있는 동안 만료시간 자동 갱신
        // leaseTime > 0 (기본 10초): 고정 leaseTime — 지정 시간 경과 후 자동 해제
        // waitTime 동안 락을 획득하지 못하면 false 반환 (블로킹 없이 빠른 실패)
        boolean isLock;
        try {
            boolean useWatchdog = distributedLock.leaseTime() < 0;
            isLock = useWatchdog
                    ? rLock.tryLock(distributedLock.waitTime(), distributedLock.timeUnit())
                    : rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit());
        } catch (InterruptedException e) {
            // 락 대기 중 스레드가 인터럽트된 경우 — 인터럽트 상태를 복원하고 예외 변환
            Thread.currentThread().interrupt();
            throw new ServiceErrorException(ERR_GET_DISTRIBUTED_LOCK_FAIL);
        }

        // waitTime 내에 락 획득 실패 시 즉시 예외 (다른 스레드가 락을 선점 중)
        if (!isLock) {
            throw new ServiceErrorException(ERR_GET_DISTRIBUTED_LOCK_FAIL);
        }

        try {
            // AopInTransaction을 통해 REQUIRES_NEW 트랜잭션 안에서 실제 메서드 실행
            // 메서드 로직 완료 + 트랜잭션 커밋이 끝난 뒤에야 finally의 락 해제가 실행됨
            return aopInTransaction.proceed(joinPoint);
        } finally {
            // 락 해제 전 현재 스레드가 락을 보유 중인지 확인
            // leaseTime 초과로 락이 이미 자동 만료된 경우 unlock() 호출 시 IllegalMonitorStateException 발생하므로 방어
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }
}
