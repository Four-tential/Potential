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

    @DistributedLock(key = "''")
    private void mockMethodWithNullKey() {
        // 이 메서드는 테스트에서 리플렉션을 통해 어노테이션 메타데이터를 추출하기 위한 용도로만 사용됩니다.
        // 실제 실행을 위한 메서드가 아닙니다.
        throw new UnsupportedOperationException("메타데이터 테스트 전용 메서드입니다.");
    }

    /**
     * withTransaction = false
     * Batch 경로: joinPoint.proceed() 직접 호출 (트랜잭션 없이 락만)
     */
    @DistributedLock(
            key = "'testKeyNoTx'",
            waitTime = 5L,
            leaseTime = 10L,
            timeUnit = TimeUnit.SECONDS,
            withTransaction = false
    )
    public void mockMethodNoTx() {
        throw new UnsupportedOperationException("메타데이터 테스트 전용 메서드입니다.");
    }

    @Test
    @DisplayName("withTransaction=true: 락 획득 성공 시 AopInTransaction 을 통해 비즈니스 로직을 실행한다")
    void lock_withTransaction_true_success() throws Throwable {
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(aopInTransaction.proceed(joinPoint)).willReturn("Success");

        Object result = distributedLockAspect.lock(joinPoint);

        assertThat(result).isEqualTo("Success");
        // AopInTransaction 을 통해 실행됐는지 검증
        verify(aopInTransaction).proceed(joinPoint);
        // joinPoint.proceed() 는 직접 호출되지 않아야 함
        verify(joinPoint, never()).proceed();
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("withTransaction=true: 락 획득 실패 시 예외를 던지고 AopInTransaction 을 호출하지 않는다")
    void lock_withTransaction_true_fail() throws Throwable {
        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(false);

        assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(ERR_GET_DISTRIBUTED_LOCK_FAIL.getMessage());

        verify(aopInTransaction, never()).proceed(any());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("withTransaction=false: 락 획득 성공 시 joinPoint.proceed() 를 직접 호출한다")
    void lock_withTransaction_false_success() throws Throwable {
        // withTransaction=false 메서드로 setUp 재설정
        Method methodNoTx = this.getClass().getDeclaredMethod("mockMethodNoTx");
        given(methodSignature.getMethod()).willReturn(methodNoTx);

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(joinPoint.proceed()).willReturn("NoTxSuccess");

        Object result = distributedLockAspect.lock(joinPoint);

        assertThat(result).isEqualTo("NoTxSuccess");
        // joinPoint.proceed() 를 직접 호출했는지 검증
        verify(joinPoint).proceed();
        // AopInTransaction 은 호출되지 않아야 함
        verify(aopInTransaction, never()).proceed(any());
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("withTransaction=false: 락 획득 실패 시 예외를 던지고 joinPoint.proceed() 를 호출하지 않는다")
    void lock_withTransaction_false_lockFail() throws Throwable {
        Method methodNoTx = this.getClass().getDeclaredMethod("mockMethodNoTx");
        given(methodSignature.getMethod()).willReturn(methodNoTx);

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(false);

        assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(ERR_GET_DISTRIBUTED_LOCK_FAIL.getMessage());

        // withTransaction=false 경로에서도 락 실패 시 둘 다 호출되지 않아야 함
        verify(aopInTransaction, never()).proceed(any());
        verify(joinPoint, never()).proceed();
    }

    @Test
    @DisplayName("withTransaction=false: 락 획득 성공 후 예외 발생해도 락은 해제된다")
    void lock_withTransaction_false_exceptionStillUnlocks() throws Throwable {
        Method methodNoTx = this.getClass().getDeclaredMethod("mockMethodNoTx");
        given(methodSignature.getMethod()).willReturn(methodNoTx);

        given(redissonClient.getLock(anyString())).willReturn(rLock);
        given(rLock.tryLock(anyLong(), anyLong(), any())).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(joinPoint.proceed()).willThrow(new RuntimeException("처리 중 예외"));

        assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("처리 중 예외");

        // 예외가 발생해도 finally 블록에서 락이 해제됐는지 검증
        verify(rLock).unlock();
    }
}
