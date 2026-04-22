package four_tential.potential.application.order;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.infra.redis.RedisConstants;
import four_tential.potential.infra.redis.RedisTestContainer;
import four_tential.potential.presentation.order.dto.OrderCreateRequest;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@Slf4j
@SpringBootTest
@ActiveProfiles("test")
class OrderFacadeConcurrencyTest extends RedisTestContainer {

    @Autowired
    private OrderFacade orderFacade;

    @Autowired
    private CourseRepository courseRepository;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RedissonClient redissonClient;

    private UUID memberId;
    private Course course1;
    private Course course2;

    @BeforeEach
    void setUp() {
        memberId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        
        // 동일 시간대 (14:00 ~ 16:00) 겹치는 두 코스 생성
        course1 = createCourse("강의1", now.withHour(14), now.withHour(16));
        course2 = createCourse("강의2", now.withHour(15), now.withHour(17));
        
        courseRepository.save(course1);
        courseRepository.save(course2);

        // Redis 잔여석 초기화 (충분히 설정)
        redissonClient.getAtomicLong(RedisConstants.COURSE_CAPACITY_PREFIX + course1.getId()).set(10);
        redissonClient.getAtomicLong(RedisConstants.COURSE_CAPACITY_PREFIX + course2.getId()).set(10);
    }

    @AfterEach
    void tearDown() {
        // 테스트 데이터 정리
        orderRepository.deleteAll();
        courseRepository.deleteAll();
        redissonClient.getAtomicLong(RedisConstants.COURSE_CAPACITY_PREFIX + course1.getId()).delete();
        redissonClient.getAtomicLong(RedisConstants.COURSE_CAPACITY_PREFIX + course2.getId()).delete();
    }

    @Test
    @DisplayName("동일 회원이 시간대가 겹치는 두 코스를 동시에 주문하면 하나만 성공해야 한다 (원자성 검증)")
    void placeOrder_concurrency_overlap_check() throws InterruptedException {
        // given
        int threadCount = 2;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failCount = new AtomicInteger(0);

        OrderCreateRequest request1 = new OrderCreateRequest(course1.getId(), 1);
        OrderCreateRequest request2 = new OrderCreateRequest(course2.getId(), 1);

        // when
        executorService.submit(() -> {
            try {
                orderFacade.placeOrder(memberId, request1);
                successCount.incrementAndGet();
            } catch (Exception e) {
                log.info("Request 1 failed: {}", e.getMessage());
                failCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        executorService.submit(() -> {
            try {
                orderFacade.placeOrder(memberId, request2);
                successCount.incrementAndGet();
            } catch (Exception e) {
                log.info("Request 2 failed: {}", e.getMessage());
                failCount.incrementAndGet();
            } finally {
                latch.countDown();
            }
        });

        latch.await();
        executorService.shutdown();

        // then
        log.info("Success count: {}, Fail count: {}", successCount.get(), failCount.get());

        assertThat(successCount.get())
                .as("동일 회원의 겹치는 시간대 주문은 하나만 성공해야 합니다")
                .isEqualTo(1);
        assertThat(failCount.get()).isEqualTo(1);
        
        long dbOrderCount = orderRepository.findAll().stream()
                .filter(o -> o.getMemberId().equals(memberId))
                .count();
        assertThat(dbOrderCount).isEqualTo(1);
    }

    private Course createCourse(String title, LocalDateTime startAt, LocalDateTime endAt) {
        return Course.register(
                UUID.randomUUID(),
                UUID.randomUUID(),
                title,
                "설명",
                "주소",
                "상세주소",
                10,
                BigInteger.valueOf(10000),
                CourseLevel.BEGINNER,
                startAt.minusDays(1),
                startAt.minusHours(3),
                startAt,
                endAt
        );
    }
}
