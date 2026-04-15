package four_tential.potential.application.order;

import four_tential.potential.infra.redis.RedisConstants;
import four_tential.potential.infra.redis.RedisTestContainer;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class WaitingListServiceConcurrencyTest extends RedisTestContainer {

    @Autowired
    private WaitingListService waitingListService;

    @Autowired
    private RedissonClient redissonClient;

    private final UUID courseId = UUID.randomUUID();

    @AfterEach
    void tearDown() {
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        String waitingKey = RedisConstants.WAITING_LIST_PREFIX + courseId;
        redissonClient.getAtomicLong(capacityKey).delete();
        redissonClient.getScoredSortedSet(waitingKey).delete();
    }

    @Test
    @DisplayName("동시에 100명이 10개 남은 좌석을 1개씩 선점하려 할 때, 정확히 10명만 성공해야 한다")
    void tryOccupyingSeat_concurrency_success() throws InterruptedException {
        // given
        int initialCapacity = 10;
        int threadCount = 100;
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        redissonClient.getAtomicLong(capacityKey).set(initialCapacity);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger exceptionCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    UUID memberId = UUID.randomUUID();
                    if (waitingListService.tryOccupyingSeat(courseId, memberId, 1)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(successCount.get())
            .withFailMessage("Expected %d successes but got %d (Exceptions: %d)", 
                initialCapacity, successCount.get(), exceptionCount.get())
            .isEqualTo(initialCapacity);
    }

    @Test
    @DisplayName("동시에 한 회원이 여러 번 좌석 선점을 시도할 때, 단 한 번만 성공해야 한다")
    void tryOccupyingSeat_duplicate_prevent_concurrency() throws InterruptedException {
        // given
        int initialCapacity = 50;
        int threadCount = 50;
        UUID memberId = UUID.randomUUID();
        String capacityKey = RedisConstants.COURSE_CAPACITY_PREFIX + courseId;
        redissonClient.getAtomicLong(capacityKey).set(initialCapacity);

        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();
        AtomicInteger exceptionCount = new AtomicInteger();

        // when
        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    if (waitingListService.tryOccupyingSeat(courseId, memberId, 1)) {
                        successCount.incrementAndGet();
                    }
                } catch (Exception e) {
                    exceptionCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }
        latch.await();

        // then
        assertThat(successCount.get())
            .withFailMessage("Expected 1 success but got %d", successCount.get())
            .isEqualTo(1);
    }
}
