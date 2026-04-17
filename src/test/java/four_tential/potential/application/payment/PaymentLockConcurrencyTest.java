package four_tential.potential.application.payment;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course_inventory.CourseInventory;
import four_tential.potential.domain.course.course_inventory.CourseInventoryRepository;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.entity.Webhook;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;
import four_tential.potential.domain.payment.enums.WebhookStatus;
import four_tential.potential.domain.payment.repository.PaymentRepository;
import four_tential.potential.domain.payment.repository.WebhookRepository;
import four_tential.potential.infra.portone.PortOneWebhookHandler;
import four_tential.potential.infra.redis.RedisTestContainer;
import io.portone.sdk.server.webhook.WebhookTransaction;
import io.portone.sdk.server.webhook.WebhookTransactionData;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigInteger;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.LockSupport;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;

@SpringBootTest
@ActiveProfiles("test")
class PaymentLockConcurrencyTest extends RedisTestContainer {

    private static final int THREAD_COUNT = 10;

    @Autowired
    private PaymentDistributedLockExecutor paymentDistributedLockExecutor;

    @Autowired
    private PaymentFacade paymentFacade;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private WebhookRepository webhookRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private CourseInventoryRepository courseInventoryRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private PortOneWebhookHandler portOneWebhookHandler;

    @AfterEach
    void tearDown() {
        webhookRepository.deleteAll();
        paymentRepository.deleteAll();
        orderRepository.deleteAll();
        courseInventoryRepository.deleteAll();
        courseRepository.deleteAll();
    }

    @Test
    @DisplayName("같은 orderId로 동시에 요청해도 분산락으로 한 번에 하나씩만 처리된다")
    void distributedLock_sameOrderId_serializesCriticalSection() throws InterruptedException {
        UUID orderId = UUID.randomUUID();
        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maxActiveCount = new AtomicInteger();
        AtomicInteger successCount = new AtomicInteger();

        ConcurrentResult result = runConcurrently(THREAD_COUNT, () ->
                paymentDistributedLockExecutor.executeWithOrderLock(orderId, () -> {
                    enterCriticalSection(activeCount, maxActiveCount);
                    try {
                        sleep(80L);
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
    @DisplayName("같은 pgKey로 동시에 요청해도 분산락으로 한 번에 하나씩만 처리된다")
    void distributedLock_samePgKey_serializesCriticalSection() throws InterruptedException {
        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maxActiveCount = new AtomicInteger();
        AtomicInteger successCount = new AtomicInteger();

        ConcurrentResult result = runConcurrently(THREAD_COUNT, () ->
                paymentDistributedLockExecutor.executeWithPgKeyLock("pg-lock-test", () -> {
                    enterCriticalSection(activeCount, maxActiveCount);
                    try {
                        sleep(80L);
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
    @DisplayName("같은 결제 row를 동시에 조회해도 비관적 락으로 한 번에 하나씩만 처리된다")
    void pessimisticLock_samePaymentRow_serializesCriticalSection() throws InterruptedException {
        String pgKey = "pg-pessimistic-lock-test";
        paymentRepository.saveAndFlush(createPendingPayment(pgKey));

        AtomicInteger activeCount = new AtomicInteger();
        AtomicInteger maxActiveCount = new AtomicInteger();
        AtomicInteger successCount = new AtomicInteger();

        ConcurrentResult result = runConcurrently(THREAD_COUNT, () ->
                transactionTemplate.execute(status -> {
                    Payment payment = paymentRepository.findByPgKeyForUpdate(pgKey).orElseThrow();

                    enterCriticalSection(activeCount, maxActiveCount);
                    try {
                        assertThat(payment.getPgKey()).isEqualTo(pgKey);
                        sleep(80L);
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
    @DisplayName("같은 pgKey의 결제 완료 웹훅이 동시에 들어와도 결제 확정과 좌석 확정은 한 번만 처리된다")
    void webhookPaid_samePgKey_confirmsPaymentAndCourseOnlyOnce() throws Exception {
        String pgKey = "pg-webhook-lock-test";
        Course course = courseRepository.saveAndFlush(CourseFixture.defaultCourse());
        CourseInventory inventory = courseInventoryRepository.saveAndFlush(
                CourseInventory.register(course.getId(), CourseFixture.DEFAULT_CAPACITY)
        );
        UUID memberId = UUID.randomUUID();
        Order order = orderRepository.saveAndFlush(Order.register(
                memberId,
                course.getId(),
                1,
                BigInteger.valueOf(1000L),
                "test course"
        ));
        paymentRepository.saveAndFlush(createPendingPayment(order.getId(), memberId, pgKey));

        given(portOneWebhookHandler.verify(anyString(), anyString(), anyString(), anyString()))
                .willReturn(new WebhookTransactionPaid(new TestWebhookTransactionData(pgKey)));

        ConcurrentResult result = runConcurrently(THREAD_COUNT, index ->
                paymentFacade.handleWebhook(
                        "{}",
                        "webhook-lock-test-" + index,
                        "timestamp",
                        "signature"
                )
        );

        Payment payment = paymentRepository.findByPgKey(pgKey).orElseThrow();
        CourseInventory updatedInventory = courseInventoryRepository.findById(course.getId()).orElseThrow();
        List<Webhook> webhooks = webhookRepository.findAll();

        assertThat(result.completed()).isTrue();
        assertThat(result.errors()).isEmpty();
        assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(payment.getPaidAt()).isNotNull();
        assertThat(updatedInventory.getConfirmCount()).isEqualTo(1);
        assertThat(webhooks)
                .hasSize(THREAD_COUNT)
                .allSatisfy(webhook -> {
                    assertThat(webhook.getStatus()).isEqualTo(WebhookStatus.COMPLETED);
                    assertThat(webhook.getPgKey()).isEqualTo(pgKey);
                    assertThat(webhook.getEventStatus()).isEqualTo("WebhookTransactionPaid");
                });
    }

    private ConcurrentResult runConcurrently(int threadCount, ThrowingRunnable task) throws InterruptedException {
        return runConcurrently(threadCount, ignored -> task.run());
    }

    private ConcurrentResult runConcurrently(int threadCount, ThrowingIndexedRunnable task) throws InterruptedException {
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        List<Throwable> errors = new CopyOnWriteArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            int index = i;
            executorService.submit(() -> {
                readyLatch.countDown();
                try {
                    startLatch.await();
                    task.run(index);
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
        boolean completed = doneLatch.await(15, TimeUnit.SECONDS);
        executorService.shutdownNow();

        return new ConcurrentResult(completed, errors);
    }

    private void enterCriticalSection(AtomicInteger activeCount, AtomicInteger maxActiveCount) {
        int active = activeCount.incrementAndGet();
        maxActiveCount.accumulateAndGet(active, Math::max);
    }

    private Payment createPendingPayment(String pgKey) {
        return Payment.createPending(
                UUID.randomUUID(),
                UUID.randomUUID(),
                null,
                pgKey,
                1000L,
                0L,
                1000L,
                PaymentPayWay.CARD
        );
    }

    private Payment createPendingPayment(UUID orderId, UUID memberId, String pgKey) {
        return Payment.createPending(
                orderId,
                memberId,
                null,
                pgKey,
                1000L,
                0L,
                1000L,
                PaymentPayWay.CARD
        );
    }

    private void sleep(long millis) {
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(millis));
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    @FunctionalInterface
    private interface ThrowingIndexedRunnable {
        void run(int index) throws Throwable;
    }

    private record ConcurrentResult(boolean completed, List<Throwable> errors) {
    }

    private static class TestWebhookTransactionData implements WebhookTransactionData {

        private final String paymentId;

        private TestWebhookTransactionData(String paymentId) {
            this.paymentId = paymentId;
        }

        @Override
        public String getPaymentId() {
            return paymentId;
        }

        @Override
        public String getStoreId() {
            return "store-1";
        }

        @Override
        public String getTransactionId() {
            return "transaction-1";
        }
    }

    private static class WebhookTransactionPaid implements WebhookTransaction {

        private final WebhookTransactionData data;

        private WebhookTransactionPaid(WebhookTransactionData data) {
            this.data = data;
        }

        @Override
        public WebhookTransactionData getData() {
            return data;
        }

        @Override
        public Instant getTimestamp() {
            return Instant.parse("2026-04-15T00:00:00Z");
        }
    }
}
