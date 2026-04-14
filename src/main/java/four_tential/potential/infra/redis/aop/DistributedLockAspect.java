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
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;

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

    @Around("@annotation(distributedLock)")
    public Object lock(ProceedingJoinPoint joinPoint, DistributedLock distributedLock) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String[] paramNameArr = signature.getParameterNames();
        Object[] argsArr = joinPoint.getArgs();

        StandardEvaluationContext standardEvaluationContext = new StandardEvaluationContext();
        for (int index = 0; index < argsArr.length; index++) {
            if (paramNameArr != null && index < paramNameArr.length && paramNameArr[index] != null) {
                standardEvaluationContext.setVariable(paramNameArr[index], argsArr[index]);
            }
        }

        String evaluatedKey = parser.parseExpression(distributedLock.key()).getValue(standardEvaluationContext, String.class);
        if (evaluatedKey == null || evaluatedKey.isBlank()) {
            throw new IllegalArgumentException("@DistributedLock 로 분산락 사용시 키 이름은 비어 있을 수 없습니다");
        }

        String key = "dLock:" + evaluatedKey;
        RLock rLock = redissonClient.getLock(key);

        // watchDog 을 먼저 활성화 하고 필요한 경우에 leaseTime 활용을 위한 분기
        boolean isLock = distributedLock.leaseTime() > 0
                ? rLock.tryLock(
                distributedLock.waitTime(),
                distributedLock.leaseTime(),
                distributedLock.timeUnit()
        )
                : rLock.tryLock(
                distributedLock.waitTime(),
                distributedLock.timeUnit()
        );

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
