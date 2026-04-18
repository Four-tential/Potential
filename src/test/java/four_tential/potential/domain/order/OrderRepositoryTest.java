package four_tential.potential.domain.order;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.infra.redis.RedisTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@Transactional
class OrderRepositoryTest extends RedisTestContainer {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private CourseRepository courseRepository;

    @Test
    @DisplayName("주문 ID와 본인 ID로 주문 상세 정보를 성공적으로 조회한다")
    void findOrderDetailsById_success() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Order order = Order.register(
                memberId,
                courseId,
                1,
                BigInteger.valueOf(50000),
                "테스트 강의"
        );
        Order savedOrder = orderRepository.save(order);
        UUID orderId = savedOrder.getId();

        // when
        Optional<Order> result = orderRepository.findOrderDetailsById(orderId, memberId);

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo(orderId);
        assertThat(result.get().getMemberId()).isEqualTo(memberId);
    }

    @Test
    @DisplayName("타인의 주문 ID로 조회 시 빈 Optional을 반환한다")
    void findOrderDetailsById_fail_unauthorized() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID anotherMemberId = UUID.randomUUID();
        Order order = Order.register(
                memberId,
                UUID.randomUUID(),
                1,
                BigInteger.valueOf(50000),
                "테스트 강의"
        );
        Order savedOrder = orderRepository.save(order);

        // when: 본인 주문이 아닌 ID로 조회 시도
        Optional<Order> result = orderRepository.findOrderDetailsById(savedOrder.getId(), anotherMemberId);

        // then
        assertThat(result).isEmpty();
    }
    
    @Test
    @DisplayName("주문 목록 조회 시 타인의 주문은 포함되지 않는다")
    void findMyOrders_exclude_others() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();

        Order myOrder = Order.register(memberId, UUID.randomUUID(), 1, BigInteger.valueOf(10000), "내 강의");
        Order otherOrder = Order.register(otherMemberId, UUID.randomUUID(), 1, BigInteger.valueOf(20000), "남의 강의");
        orderRepository.save(myOrder);
        orderRepository.save(otherOrder);

        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);

        // when
        org.springframework.data.domain.Page<Order> result = orderRepository.findMyOrders(memberId, pageRequest);

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getMemberId()).isEqualTo(memberId);
        assertThat(result.getTotalElements()).isEqualTo(1);
    }
    
    @Test
    @DisplayName("본인의 주문 목록을 최신순으로 페이징하여 조회한다")
    void findMyOrders_success() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId1 = UUID.randomUUID();
        UUID courseId2 = UUID.randomUUID();

        Order order1 = Order.register(memberId, courseId1, 1, BigInteger.valueOf(10000), "강의 1");
        Order order2 = Order.register(memberId, courseId2, 2, BigInteger.valueOf(20000), "강의 2");
        orderRepository.save(order1);
        orderRepository.save(order2);

        org.springframework.data.domain.PageRequest pageRequest = org.springframework.data.domain.PageRequest.of(0, 10);
        // when
        org.springframework.data.domain.Page<Order> result = orderRepository.findMyOrders(memberId, pageRequest);
        // then
        assertThat(result.getContent()).hasSize(2);
        // 최신순 정렬 확인 (order2가 나중에 생성되었으므로 첫 번째여야 함)
        assertThat(result.getContent().get(0).getCourseId()).isEqualTo(courseId2);
        assertThat(result.getContent().get(1).getCourseId()).isEqualTo(courseId1);
        assertThat(result.getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("활성 수강 조회 - PAID 주문과 종료 전 OPEN 코스가 있으면 true")
    void existsActiveEnrollment_paidOpenFutureCourse_returnsTrue() {
        UUID memberId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Course course = openCourse(now.plusDays(7), now.plusDays(7).plusHours(2));
        Course savedCourse = courseRepository.save(course);
        Order order = paidOrder(memberId, savedCourse.getId());
        orderRepository.save(order);

        boolean exists = orderRepository.existsActiveEnrollment(
                memberId,
                List.of(OrderStatus.PAID, OrderStatus.CONFIRMED),
                List.of(CourseStatus.OPEN),
                now
        );

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("활성 수강 조회 - 종료된 OPEN 코스만 있으면 false")
    void existsActiveEnrollment_endedCourse_returnsFalse() {
        UUID memberId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Course course = openCourse(now.minusDays(2), now.minusDays(2).plusHours(2));
        Course savedCourse = courseRepository.save(course);
        Order order = paidOrder(memberId, savedCourse.getId());
        orderRepository.save(order);

        boolean exists = orderRepository.existsActiveEnrollment(
                memberId,
                List.of(OrderStatus.PAID, OrderStatus.CONFIRMED),
                List.of(CourseStatus.OPEN),
                now
        );

        assertThat(exists).isFalse();
    }

    @Test
    @DisplayName("활성 수강 조회 - 주문 상태가 대상 상태가 아니면 false")
    void existsActiveEnrollment_pendingOrder_returnsFalse() {
        UUID memberId = UUID.randomUUID();
        LocalDateTime now = LocalDateTime.now();
        Course course = openCourse(now.plusDays(7), now.plusDays(7).plusHours(2));
        Course savedCourse = courseRepository.save(course);
        Order order = Order.register(memberId, savedCourse.getId(), 1, BigInteger.valueOf(50000), "테스트 강의");
        orderRepository.save(order);

        boolean exists = orderRepository.existsActiveEnrollment(
                memberId,
                List.of(OrderStatus.PAID, OrderStatus.CONFIRMED),
                List.of(CourseStatus.OPEN),
                now
        );

        assertThat(exists).isFalse();
    }
    
    @Test
    @DisplayName("수강생 합산 조회 - PAID 주문의 orderCount 합산 반환")
    void sumStudentCount_paidOrders_returnsSumOfOrderCount() {
        UUID instructorId = UUID.randomUUID();
        Course course = courseRepository.save(courseForInstructor(instructorId));
        orderRepository.save(paidOrderWithCount(UUID.randomUUID(), course.getId(), 3));

        Long result = orderRepository.sumStudentCountByMemberInstructorIdAndStatusIn(
                instructorId, List.of(OrderStatus.PAID, OrderStatus.CONFIRMED));

        assertThat(result).isEqualTo(3L);
    }

    @Test
    @DisplayName("수강생 합산 조회 - CONFIRMED 주문도 합산에 포함")
    void sumStudentCount_confirmedOrders_included() {
        UUID instructorId = UUID.randomUUID();
        Course course = courseRepository.save(courseForInstructor(instructorId));
        Order order = Order.register(UUID.randomUUID(), course.getId(), 2, BigInteger.valueOf(50000), "테스트");
        ReflectionTestUtils.setField(order, "status", OrderStatus.CONFIRMED);
        orderRepository.save(order);

        Long result = orderRepository.sumStudentCountByMemberInstructorIdAndStatusIn(
                instructorId, List.of(OrderStatus.PAID, OrderStatus.CONFIRMED));

        assertThat(result).isEqualTo(2L);
    }

    @Test
    @DisplayName("수강생 합산 조회 - 해당 강사의 주문이 없으면 0 반환")
    void sumStudentCount_noOrders_returnsZero() {
        UUID instructorId = UUID.randomUUID();

        Long result = orderRepository.sumStudentCountByMemberInstructorIdAndStatusIn(
                instructorId, List.of(OrderStatus.PAID, OrderStatus.CONFIRMED));

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("수강생 합산 조회 - PENDING 주문은 합산에서 제외")
    void sumStudentCount_pendingOrder_excluded() {
        UUID instructorId = UUID.randomUUID();
        Course course = courseRepository.save(courseForInstructor(instructorId));
        // PENDING 상태 주문 (결제 완료 전)
        orderRepository.save(Order.register(UUID.randomUUID(), course.getId(), 5, BigInteger.valueOf(50000), "테스트"));

        Long result = orderRepository.sumStudentCountByMemberInstructorIdAndStatusIn(
                instructorId, List.of(OrderStatus.PAID, OrderStatus.CONFIRMED));

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("수강생 합산 조회 - 다른 강사의 주문은 합산에서 제외")
    void sumStudentCount_otherInstructor_excluded() {
        UUID myInstructorId = UUID.randomUUID();
        UUID otherInstructorId = UUID.randomUUID();
        Course otherCourse = courseRepository.save(courseForInstructor(otherInstructorId));
        orderRepository.save(paidOrderWithCount(UUID.randomUUID(), otherCourse.getId(), 10));

        Long result = orderRepository.sumStudentCountByMemberInstructorIdAndStatusIn(
                myInstructorId, List.of(OrderStatus.PAID, OrderStatus.CONFIRMED));

        assertThat(result).isZero();
    }

    @Test
    @DisplayName("수강생 합산 조회 - PAID·CONFIRMED 여러 주문의 orderCount 합산")
    void sumStudentCount_multipleOrders_returnsTotalSum() {
        UUID instructorId = UUID.randomUUID();
        Course course = courseRepository.save(courseForInstructor(instructorId));

        orderRepository.save(paidOrderWithCount(UUID.randomUUID(), course.getId(), 3));
        orderRepository.save(paidOrderWithCount(UUID.randomUUID(), course.getId(), 2));

        Order confirmedOrder = Order.register(UUID.randomUUID(), course.getId(), 4, BigInteger.valueOf(50000), "테스트");
        ReflectionTestUtils.setField(confirmedOrder, "status", OrderStatus.CONFIRMED);
        orderRepository.save(confirmedOrder);

        Long result = orderRepository.sumStudentCountByMemberInstructorIdAndStatusIn(
                instructorId, List.of(OrderStatus.PAID, OrderStatus.CONFIRMED));

        assertThat(result).isEqualTo(9L);
    }

    @Test
    @DisplayName("수강생 합산 조회 - PAID와 PENDING이 섞여 있으면 PAID만 합산")
    void sumStudentCount_mixedStatus_onlyPaidCounted() {
        UUID instructorId = UUID.randomUUID();
        Course course = courseRepository.save(courseForInstructor(instructorId));

        orderRepository.save(paidOrderWithCount(UUID.randomUUID(), course.getId(), 4));
        // PENDING 주문은 제외
        orderRepository.save(Order.register(UUID.randomUUID(), course.getId(), 10, BigInteger.valueOf(50000), "테스트"));

        Long result = orderRepository.sumStudentCountByMemberInstructorIdAndStatusIn(
                instructorId, List.of(OrderStatus.PAID, OrderStatus.CONFIRMED));

        assertThat(result).isEqualTo(4L);
    }

    private Course courseForInstructor(UUID memberInstructorId) {
        LocalDateTime now = LocalDateTime.now();
        return Course.register(
                UUID.randomUUID(),
                memberInstructorId,
                "테스트 강의",
                "테스트 설명",
                "서울특별시 강남구",
                "테헤란로 123",
                20,
                BigInteger.valueOf(50000),
                CourseLevel.BEGINNER,
                now.plusDays(1),
                now.plusDays(2),
                now.plusDays(3),
                now.plusDays(10)
        );
    }

    private Order paidOrderWithCount(UUID memberId, UUID courseId, int orderCount) {
        Order order = Order.register(memberId, courseId, orderCount, BigInteger.valueOf(50000), "테스트 강의");
        order.completePayment();
        return order;
    }

    private Course openCourse(LocalDateTime startAt, LocalDateTime endAt) {
        Course course = Course.register(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "테스트 강의",
                "테스트 설명",
                "서울특별시 강남구",
                "테헤란로 123",
                20,
                BigInteger.valueOf(50000),
                CourseLevel.BEGINNER,
                startAt.minusDays(10),
                startAt.minusDays(1),
                startAt,
                endAt
        );
        course.open();
        return course;
    }

    private Order paidOrder(UUID memberId, UUID courseId) {
        Order order = Order.register(memberId, courseId, 1, BigInteger.valueOf(50000), "테스트 강의");
        order.completePayment();
        return order;
    }
}
