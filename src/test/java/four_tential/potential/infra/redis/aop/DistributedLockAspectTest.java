package four_tential.potential.infra.redis.aop;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.redis.annotation.DistributedLock;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

import static four_tential.potential.common.exception.domain.CommonExceptionEnum.ERR_DISTRIBUTED_LOCK_KEY_NULL;
import static four_tential.potential.common.exception.domain.CommonExceptionEnum.ERR_GET_DISTRIBUTED_LOCK_FAIL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockAspectTest {

    @Mock private RedissonClient redissonClient;
    @Mock private AopInTransaction aopInTransaction;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature methodSignature;
    @Mock private RLock rLock;

    @InjectMocks private DistributedLockAspect distributedLockAspect;

    @BeforeEach
    void setUp() throws NoSuchMethodException {
        Method method = this.getClass().getDeclaredMethod("mockMethod");
        given(joinPoint.getSignature()).willReturn(methodSignature);
        given(methodSignature.getMethod()).willReturn(method);
        given(joinPoint.getArgs()).willReturn(new Object[]{});
    }

    @DistributedLock(key = "'testKey'", waitTime = 5L, leaseTime = 10L, timeUnit = TimeUnit.SECONDS)
    public void mockMethod() {
        // 이 메서드는 테스트에서 리플렉션을 통해 어노테이션 메타데이터를 추출하기 위한 용도로만 사용됩니다.
        // 실제 실행을 위한 메서드가 아닙니다.
        throw new UnsupportedOperationException("메타데이터 테스트 전용 메서드입니다.");
    }

    @Test
    @DisplayName("락 획득 성공 시 비즈니스 로직을 실행한다")
    void lock_success() throws Throwable {
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(aopInTransaction.proceed(joinPoint)).willReturn("Success");

        Object result = distributedLockAspect.lock(joinPoint);

        assertThat(result).isEqualTo("Success");
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 시 예외를 던진다")
    void lock_fail() throws Throwable {
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(false);

        assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(ERR_GET_DISTRIBUTED_LOCK_FAIL.getMessage());
        
        verify(aopInTransaction, never()).proceed(any());
    }

    @Test
    @DisplayName("락 키가 비어있으면 예외를 던진다")
    void lock_key_null() throws NoSuchMethodException {
        Method methodWithNullKey = this.getClass().getDeclaredMethod("mockMethodWithNullKey");
        given(methodSignature.getMethod()).willReturn(methodWithNullKey);

        assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(ERR_DISTRIBUTED_LOCK_KEY_NULL.getMessage());
    }

    @DistributedLock(key = "")
    private void mockMethodWithNullKey() {
        // 이 메서드는 테스트에서 리플렉션을 통해 어노테이션 메타데이터를 추출하기 위한 용도로만 사용됩니다.
        // 실제 실행을 위한 메서드가 아닙니다.
        throw new UnsupportedOperationException("메타데이터 테스트 전용 메서드입니다.");
    }
}
