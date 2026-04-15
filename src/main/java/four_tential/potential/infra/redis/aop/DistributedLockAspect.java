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

@Slf4j
@Aspect
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class DistributedLockAspect {
    private final RedissonClient redissonClient;
    private final AopInTransaction aopInTransaction;

    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer nameDiscoverer = new DefaultParameterNameDiscoverer();

    /**
     * 분산 락 적용 Aspect
     * - 포인트컷 바인딩 오류 방지를 위해 어노테이션 객체는 메서드 내부에서 직접 추출합니다.
     */
    @Around("@annotation(four_tential.potential.infra.redis.annotation.DistributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        DistributedLock distributedLock = method.getAnnotation(DistributedLock.class);
        
        Object[] argsArr = joinPoint.getArgs();
        String[] paramNames = nameDiscoverer.getParameterNames(method);

        StandardEvaluationContext context = new StandardEvaluationContext();
        
        if (paramNames != null) {
            for (int i = 0; i < paramNames.length; i++) {
                context.setVariable(paramNames[i], argsArr[i]);
            }
        }
        
        for (int i = 0; i < argsArr.length; i++) {
            context.setVariable("p" + i, argsArr[i]);
            context.setVariable("arg" + i, argsArr[i]);
        }

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

        String key = "dLock:" + evaluatedKey;
        RLock rLock = redissonClient.getLock(key);

        boolean isLock;
        try {
            isLock = distributedLock.leaseTime() > 0
                    ? rLock.tryLock(distributedLock.waitTime(), distributedLock.leaseTime(), distributedLock.timeUnit())
                    : rLock.tryLock(distributedLock.waitTime(), distributedLock.timeUnit());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServiceErrorException(ERR_GET_DISTRIBUTED_LOCK_FAIL);
        }

        if (!isLock) {
            throw new ServiceErrorException(ERR_GET_DISTRIBUTED_LOCK_FAIL);
        }

        try {
            return aopInTransaction.proceed(joinPoint);
        } finally {
            if (rLock.isHeldByCurrentThread()) {
                rLock.unlock();
            }
        }
    }
}
