package four_tential.potential.application.payment;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Refund;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.enums.RefundStatus;
import four_tential.potential.domain.payment.port.PaymentGateway;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import four_tential.potential.domain.payment.repository.RefundRepository;
import four_tential.potential.infra.redis.RedisTestContainer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.LockSupport;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;

@SpringBootTest
@ActiveProfiles("test")
class RefundLockConcurrencyTest extends RedisTestContainer {

    private static final int THREAD_COUNT = 5;

    @Autowired
    private PaymentDistributedLockExecutor paymentLockExecutor;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundRepository refundRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    /**
     * PortOne API 호출은 실제로 하지 않는다.
     * 테스트는 락 + DB 정합성에 집중한다.
     */
    @MockitoBean
    private PaymentGateway paymentGateway;

    @AfterEach
    void tearDown() {
        refundRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 pgKey 로 동시에 환불 분산락을 요청해도 한 번에 하나씩만 임계 구역에 진입한다")
    void refundDistributedLock_samePgKey_serializesCriticalSection() throws InterruptedException {
        String pgKey = "pg-refund-lock-test";
        AtomicInteger activeCount    = new AtomicInteger();
        AtomicInteger maxActiveCount = new AtomicInteger();
        AtomicInteger successCount   = new AtomicInteger();

        ConcurrentResult result = runConcurrently(THREAD_COUNT, () ->
                paymentLockExecutor.executeWithPgKeyLock(pgKey, () -> {
                    // 임계 구역 진입
                    int active = activeCount.incrementAndGet();
                    maxActiveCount.accumulateAndGet(active, Math::max);
                    try {
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
                        successCount.incrementAndGet();
                        return null;
                    } finally {
                        activeCount.decrementAndGet();
                    }
                })
        );

        assertThat(result.completed()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        // 분산락이 정상이라면 동시에 임계 구역에 진입한 스레드는 최대 1개
        assertThat(maxActiveCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 Payment row 를 동시에 FOR UPDATE 조회해도 비관적 락으로 순서대로 처리된다")
    void pessimisticLock_samePaymentRow_serializesCriticalSection() throws InterruptedException {
        String pgKey = "pg-pessimistic-refund-test";
        paymentRepository.saveAndFlush(createPendingPayment(pgKey));

        AtomicInteger activeCount    = new AtomicInteger();
        AtomicInteger maxActiveCount = new AtomicInteger();
        AtomicInteger successCount   = new AtomicInteger();

        ConcurrentResult result = runConcurrently(THREAD_COUNT, () ->
                transactionTemplate.execute(status -> {
                    Payment payment = paymentRepository.findByPgKeyForUpdate(pgKey).orElseThrow();
                    int active = activeCount.incrementAndGet();
                    maxActiveCount.accumulateAndGet(active, Math::max);
                    try {
                        assertThat(payment.getPgKey()).isEqualTo(pgKey);
                        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(50));
                        successCount.incrementAndGet();
                        return null;
                    } finally {
                        activeCount.decrementAndGet();
                    }
                })
        );

        assertThat(result.completed()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(successCount.get()).isEqualTo(THREAD_COUNT);
        assertThat(maxActiveCount.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("동시에 전액 환불 요청이 들어와도 COMPLETED 환불 이력은 정확히 1건만 생성된다")
    void concurrentFullRefund_createsExactlyOneCompletedRefund() throws InterruptedException {
        // PortOne API 는 항상 성공으로 처리
        doNothing().when(paymentGateway).cancelPayment(any());

        String pgKey = "pg-concurrent-refund";
        Course course = courseRepository.saveAndFlush(CourseFixture.defaultCourse());
        UUID memberId = UUID.randomUUID();
        Order order = orderRepository.saveAndFlush(Order.register(
                memberId,
                course.getId(),
                1,
                BigInteger.valueOf(10000L),
                "테스트 강좌"
        ));
        order.completePayment();
        orderRepository.saveAndFlush(order);

        Payment payment = paymentRepository.saveAndFlush(createPendingPayment(order.getId(), memberId, pgKey, 10000L));
        payment.confirmPaid();
        paymentRepository.saveAndFlush(payment);

        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger failCount    = new AtomicInteger();

        // 동시에 THREAD_COUNT 개의 전액 환불 요청 실행
        // 분산락 + 비관적 락으로 1건만 통과하고 나머지는 예외 처리
        ConcurrentResult result = runConcurrently(THREAD_COUNT, () ->
                paymentLockExecutor.executeWithPgKeyLock(pgKey, () -> {
                    try {
                        transactionTemplate.execute(status -> {
                            Payment p = paymentRepository.findByPgKeyForUpdate(pgKey).orElseThrow();
                            if (p.getStatus() != PaymentStatus.PAID
                                    && p.getStatus() != PaymentStatus.PART_REFUNDED) {
                                status.setRollbackOnly();
                                return null;
                            }
                            Refund refund = Refund.completed(p, p.getPaidTotalPrice(), 1,
                                    four_tential.potential.domain.payment.enums.RefundReason.CANCEL);
                            refundRepository.save(refund);
                            p.refund();
                            paymentRepository.save(p);
                            return null;
                        });
                        successCount.incrementAndGet();
                    } catch (Exception e) {
                        failCount.incrementAndGet();
                    }
                    return null;
                })
        );

        assertThat(result.completed()).isTrue();

        // 락으로 인해 실제 환불 처리는 1번만 성공
        List<Refund> refunds = refundRepository.findAll();
        long completedRefunds = refunds.stream()
                .filter(r -> r.getStatus() == RefundStatus.COMPLETED)
                .count();
        assertThat(completedRefunds).isEqualTo(1);

        // 결제 상태는 REFUNDED 여야 한다
        Payment updatedPayment = paymentRepository.findByPgKey(pgKey).orElseThrow();
        assertThat(updatedPayment.getStatus())
                .isEqualTo(four_tential.potential.domain.payment.enums.PaymentStatus.REFUNDED);
    }

    private ConcurrentResult runConcurrently(int threadCount, ThrowingRunnable task) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch  = new CountDownLatch(threadCount);
        List<Throwable> errors    = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    task.run();
                } catch (Throwable e) {
                    errors.add(e);
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        boolean ready = readyLatch.await(5, TimeUnit.SECONDS);
        assertThat(ready).isTrue();
        startLatch.countDown();
        boolean completed = doneLatch.await(30, TimeUnit.SECONDS);
        executorService.shutdownNow();

        return new ConcurrentResult(completed, errors);
    }

    private Payment createPendingPayment(String pgKey) {
        return Payment.createPending(
                UUID.randomUUID(), UUID.randomUUID(),
                pgKey, 10000L, 10000L, PaymentPayWay.CARD
        );
    }

    private Payment createPendingPayment(UUID orderId, UUID memberId, String pgKey, Long amount) {
        return Payment.createPending(
                orderId, memberId,
                pgKey, amount, amount, PaymentPayWay.CARD
        );
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private record ConcurrentResult(boolean completed, List<Throwable> errors) {}
}
