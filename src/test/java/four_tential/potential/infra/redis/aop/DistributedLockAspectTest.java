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

import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DistributedLockAspectTest {

    @Mock private RedissonClient redissonClient;
    @Mock private AopInTransaction aopInTransaction;
    @Mock private ProceedingJoinPoint joinPoint;
    @Mock private MethodSignature methodSignature;
    @Mock private RLock rLock;
    @Mock private DistributedLock distributedLock;

    @InjectMocks private DistributedLockAspect distributedLockAspect;

    @BeforeEach
    void setUp() {
        lenient().when(joinPoint.getSignature()).thenReturn(methodSignature);
        lenient().when(methodSignature.getParameterNames()).thenReturn(new String[]{});
        lenient().when(joinPoint.getArgs()).thenReturn(new Object[]{});
        lenient().when(distributedLock.timeUnit()).thenReturn(TimeUnit.SECONDS);
        lenient().when(distributedLock.waitTime()).thenReturn(5L);
        lenient().when(distributedLock.leaseTime()).thenReturn(10L);
    }

    @Test
    @DisplayName("락 획득 성공 - 비즈니스 로직 실행 및 락 해제")
    void lock_success() throws Throwable {
        given(distributedLock.key()).willReturn("'testKey'");
        given(redissonClient.getLock("dLock:testKey")).willReturn(rLock);
        given(rLock.tryLock(5L, 10L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(aopInTransaction.proceed(joinPoint)).willReturn("result");

        Object result = distributedLockAspect.lock(joinPoint, distributedLock);

        assertThat(result).isEqualTo("result");
        verify(aopInTransaction).proceed(joinPoint);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("락 획득 실패 - ServiceErrorException 발생")
    void lock_acquireFail_throwsServiceErrorException() throws Throwable {
        given(distributedLock.key()).willReturn("'testKey'");
        given(redissonClient.getLock("dLock:testKey")).willReturn(rLock);
        given(rLock.tryLock(5L, 10L, TimeUnit.SECONDS)).willReturn(false);

        assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint, distributedLock))
                .isInstanceOf(ServiceErrorException.class);
        verify(aopInTransaction, never()).proceed(any());
        verify(rLock, never()).unlock();
    }

    @Test
    @DisplayName("빈 키 - ServiceErrorException 발생")
    void lock_blankKey_throwsIllegalArgumentException() {
        given(distributedLock.key()).willReturn("''");

        assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint, distributedLock))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessageContaining("락의 키는 비어있을 수 없습니다");
    }

    @Test
    @DisplayName("leaseTime <= 0 - watchdog 모드로 tryLock 호출")
    void lock_watchdogMode() throws Throwable {
        given(distributedLock.key()).willReturn("'testKey'");
        given(distributedLock.leaseTime()).willReturn(-1L);
        given(redissonClient.getLock("dLock:testKey")).willReturn(rLock);
        given(rLock.tryLock(5L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(aopInTransaction.proceed(joinPoint)).willReturn(null);

        distributedLockAspect.lock(joinPoint, distributedLock);

        verify(rLock).tryLock(5L, TimeUnit.SECONDS);
        verify(rLock, never()).tryLock(anyLong(), anyLong(), any());
        verify(aopInTransaction).proceed(joinPoint);
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("proceed 중 예외 발생 - 예외 전파 및 락 해제")
    void lock_exceptionDuringProceed_releasesLock() throws Throwable {
        given(distributedLock.key()).willReturn("'testKey'");
        given(redissonClient.getLock("dLock:testKey")).willReturn(rLock);
        given(rLock.tryLock(5L, 10L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(aopInTransaction.proceed(joinPoint)).willThrow(new RuntimeException("비즈니스 로직 예외"));

        assertThatThrownBy(() -> distributedLockAspect.lock(joinPoint, distributedLock))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("비즈니스 로직 예외");
        verify(rLock).unlock();
    }

    @Test
    @DisplayName("SpEL 키 파싱 - 메서드 파라미터에서 키 추출")
    void lock_spelKeyFromParameter() throws Throwable {
        given(methodSignature.getParameterNames()).willReturn(new String[]{"orderId"});
        given(joinPoint.getArgs()).willReturn(new Object[]{42L});
        given(distributedLock.key()).willReturn("#orderId");
        given(redissonClient.getLock("dLock:42")).willReturn(rLock);
        given(rLock.tryLock(5L, 10L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(true);
        given(aopInTransaction.proceed(joinPoint)).willReturn(null);

        distributedLockAspect.lock(joinPoint, distributedLock);

        verify(redissonClient).getLock("dLock:42");
    }

    @Test
    @DisplayName("현재 스레드가 락 미보유 - unlock 미호출")
    void lock_notHeldByCurrentThread_doesNotUnlock() throws Throwable {
        given(distributedLock.key()).willReturn("'testKey'");
        given(redissonClient.getLock("dLock:testKey")).willReturn(rLock);
        given(rLock.tryLock(5L, 10L, TimeUnit.SECONDS)).willReturn(true);
        given(rLock.isHeldByCurrentThread()).willReturn(false);
        given(aopInTransaction.proceed(joinPoint)).willReturn(null);

        distributedLockAspect.lock(joinPoint, distributedLock);

        verify(rLock, never()).unlock();
    }
}
