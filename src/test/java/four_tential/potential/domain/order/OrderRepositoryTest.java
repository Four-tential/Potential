package four_tential.potential.domain.order;

import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.infra.redis.RedisTestContainer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
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

    @Autowired
    private MemberRepository memberRepository;

    @Autowired
    private AttendanceRepository attendanceRepository;

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

    // ── findConfirmedStudentsByCourseId ──────────────────────────────────────

    @Test
    @DisplayName("수강생 명단 조회 - CONFIRMED 주문과 ATTEND 출석 정보가 있으면 출석 상태 포함 반환")
    void findConfirmedStudents_withAttendance_returnsAttendStatus() {
        // given
        UUID courseId = UUID.randomUUID();
        Member member = memberRepository.save(
                Member.register("attend@test.com", "pw", "김수강", "010-1111-1111"));
        Order order = confirmedOrder(member.getId(), courseId);
        Order saved = orderRepository.save(order);

        Attendance attendance = Attendance.register(saved.getId(), member.getId(), courseId);
        attendance.attend("qr-token-1");
        attendanceRepository.save(attendance);

        // when
        Page<CourseStudentQueryResult> result =
                orderRepository.findConfirmedStudentsByCourseId(courseId, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).memberId()).isEqualTo(member.getId());
        assertThat(result.getContent().get(0).memberName()).isEqualTo("김수강");
        assertThat(result.getContent().get(0).attendanceStatus()).isEqualTo(AttendanceStatus.ATTEND);
        assertThat(result.getContent().get(0).attendanceAt()).isNotNull();
    }

    @Test
    @DisplayName("수강생 명단 조회 - CONFIRMED 주문에 출석 미처리(ABSENT) 상태로 저장된 수강생도 포함")
    void findConfirmedStudents_withAbsentAttendance_returnsAbsentStatus() {
        // given
        UUID courseId = UUID.randomUUID();
        Member member = memberRepository.save(
                Member.register("absent@test.com", "pw", "이결석", "010-2222-2222"));
        Order order = confirmedOrder(member.getId(), courseId);
        Order saved = orderRepository.save(order);

        // Attendance.register 시 기본 status = ABSENT
        attendanceRepository.save(Attendance.register(saved.getId(), member.getId(), courseId));

        // when
        Page<CourseStudentQueryResult> result =
                orderRepository.findConfirmedStudentsByCourseId(courseId, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).attendanceStatus()).isEqualTo(AttendanceStatus.ABSENT);
        assertThat(result.getContent().get(0).attendanceAt()).isNull();
    }

    @Test
    @DisplayName("수강생 명단 조회 - 출석 레코드가 없어도 수강생은 null 출석 상태로 포함")
    void findConfirmedStudents_noAttendance_returnsNullStatus() {
        // given
        UUID courseId = UUID.randomUUID();
        Member member = memberRepository.save(
                Member.register("noattend@test.com", "pw", "박미출", "010-3333-3333"));
        orderRepository.save(confirmedOrder(member.getId(), courseId));

        // when
        Page<CourseStudentQueryResult> result =
                orderRepository.findConfirmedStudentsByCourseId(courseId, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).memberName()).isEqualTo("박미출");
        assertThat(result.getContent().get(0).attendanceStatus()).isNull();
        assertThat(result.getContent().get(0).attendanceAt()).isNull();
    }

    @Test
    @DisplayName("수강생 명단 조회 - CONFIRMED 상태가 아닌 주문(PAID, PENDING)은 포함하지 않음")
    void findConfirmedStudents_excludesNonConfirmedOrders() {
        // given
        UUID courseId = UUID.randomUUID();
        Member member1 = memberRepository.save(
                Member.register("paid@test.com", "pw", "결제완료", "010-4444-4444"));
        Member member2 = memberRepository.save(
                Member.register("pending@test.com", "pw", "결제대기", "010-5555-5555"));

        // PAID 주문 (CONFIRMED 아님)
        Order paidOrder = Order.register(member1.getId(), courseId, 1, BigInteger.valueOf(50000), "테스트");
        paidOrder.completePayment();
        orderRepository.save(paidOrder);

        // PENDING 주문
        orderRepository.save(Order.register(member2.getId(), courseId, 1, BigInteger.valueOf(50000), "테스트"));

        // when
        Page<CourseStudentQueryResult> result =
                orderRepository.findConfirmedStudentsByCourseId(courseId, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).isEmpty();
        assertThat(result.getTotalElements()).isZero();
    }

    @Test
    @DisplayName("수강생 명단 조회 - 다른 코스의 CONFIRMED 주문은 포함하지 않음")
    void findConfirmedStudents_excludesOtherCourse() {
        // given
        UUID targetCourseId = UUID.randomUUID();
        UUID otherCourseId = UUID.randomUUID();

        Member member = memberRepository.save(
                Member.register("other@test.com", "pw", "다른코스", "010-6666-6666"));

        // 다른 코스의 CONFIRMED 주문
        orderRepository.save(confirmedOrder(member.getId(), otherCourseId));

        // when
        Page<CourseStudentQueryResult> result =
                orderRepository.findConfirmedStudentsByCourseId(targetCourseId, PageRequest.of(0, 10));

        // then
        assertThat(result.getContent()).isEmpty();
    }

    @Test
    @DisplayName("수강생 명단 조회 - 페이징이 올바르게 동작한다")
    void findConfirmedStudents_pagination_correct() {
        // given
        UUID courseId = UUID.randomUUID();
        for (int i = 1; i <= 3; i++) {
            Member m = memberRepository.save(
                    Member.register("student" + i + "@test.com", "pw", "수강생" + i, "010-000" + i + "-0000"));
            orderRepository.save(confirmedOrder(m.getId(), courseId));
        }

        // when: 첫 페이지 (size=2)
        Page<CourseStudentQueryResult> page0 =
                orderRepository.findConfirmedStudentsByCourseId(courseId, PageRequest.of(0, 2));

        // then
        assertThat(page0.getContent()).hasSize(2);
        assertThat(page0.getTotalElements()).isEqualTo(3);
        assertThat(page0.getTotalPages()).isEqualTo(2);
        assertThat(page0.isLast()).isFalse();

        // when: 두 번째 페이지
        Page<CourseStudentQueryResult> page1 =
                orderRepository.findConfirmedStudentsByCourseId(courseId, PageRequest.of(1, 2));

        // then
        assertThat(page1.getContent()).hasSize(1);
        assertThat(page1.isLast()).isTrue();
    }

    @Test
    @DisplayName("확정 대상 주문 조회 - PAID 상태이고 코스 시작 7일 이내(환불 불가 기간 진입)인 주문만 조회한다")
    void findPaidOrdersToConfirm_success() {
        // given
        // 시간 정밀도 문제를 방지하기 위해 초 단위까지만 사용
        LocalDateTime now = LocalDateTime.now().withNano(0);
        
        // 1. 대상: PAID, 지금으로부터 6일 후 시작 (7일 이내이므로 환불 불가/확정 대상)
        Course course1 = courseRepository.save(openCourse(now.plusDays(6), now.plusDays(6).plusHours(2)));
        Order order1 = paidOrder(UUID.randomUUID(), course1.getId());
        orderRepository.save(order1);

        // 2. 제외: PAID, 지금으로부터 8일 후 시작 (7일보다 많이 남았으므로 환불 가능/확정 제외)
        Course course2 = courseRepository.save(openCourse(now.plusDays(8), now.plusDays(8).plusHours(2)));
        orderRepository.save(paidOrder(UUID.randomUUID(), course2.getId()));

        // 3. 제외: PENDING, 지금으로부터 6일 후 시작 (상태가 PAID가 아니므로 제외)
        Course course3 = courseRepository.save(openCourse(now.plusDays(6), now.plusDays(6).plusHours(2)));
        orderRepository.save(Order.register(UUID.randomUUID(), course3.getId(), 1, BigInteger.valueOf(50000), "테스트"));

        // 4. 대상: PAID, 지금으로부터 7일 미만(예: 6일 23시간 59분) 후 시작 (경계값, 확정 대상 포함)
        Course course4 = courseRepository.save(openCourse(now.plusDays(7).minusMinutes(1), now.plusDays(7).plusHours(2)));
        Order order4 = paidOrder(UUID.randomUUID(), course4.getId());
        orderRepository.save(order4);

        // 5. 제외: PAID, 정확히 7일 후 시작 (취소 가능 경계이므로 확정 대상 아님)
        Course course5 = courseRepository.save(openCourse(now.plusDays(7), now.plusDays(7).plusHours(2)));
        Order order5 = paidOrder(UUID.randomUUID(), course5.getId());
        orderRepository.save(order5);

        // when
        List<Order> result = orderRepository.findPaidOrdersToConfirm(now, PageRequest.of(0, 10));

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Order::getId)
                .containsExactlyInAnyOrder(order1.getId(), order4.getId());
        assertThat(result).extracting(Order::getId).doesNotContain(order5.getId());
    }

    @Test
    @DisplayName("상태별 좌석 합계 조회 - 특정 코스의 PENDING, PAID, CONFIRMED 좌석만 합산한다")
    void sumOrderCountByCourseIdAndStatuses_success() {
        // given
        UUID courseId = UUID.randomUUID();
        
        // 1. PENDING (합산 대상)
        orderRepository.save(Order.register(UUID.randomUUID(), courseId, 2, BigInteger.valueOf(10000), "테스트1"));
        
        // 2. PAID (합산 대상)
        Order paidOrder = Order.register(UUID.randomUUID(), courseId, 3, BigInteger.valueOf(10000), "테스트2");
        paidOrder.completePayment();
        orderRepository.save(paidOrder);
        
        // 3. CONFIRMED (합산 대상)
        Order confirmedOrder = Order.register(UUID.randomUUID(), courseId, 5, BigInteger.valueOf(10000), "테스트3");
        ReflectionTestUtils.setField(confirmedOrder, "status", OrderStatus.CONFIRMED);
        orderRepository.save(confirmedOrder);
        
        // 4. CANCELLED (합산 제외)
        Order cancelledOrder = Order.register(UUID.randomUUID(), courseId, 10, BigInteger.valueOf(10000), "테스트4");
        ReflectionTestUtils.setField(cancelledOrder, "status", OrderStatus.CANCELLED);
        orderRepository.save(cancelledOrder);
        
        // 5. 다른 코스 주문 (합산 제외)
        orderRepository.save(Order.register(UUID.randomUUID(), UUID.randomUUID(), 100, BigInteger.valueOf(10000), "다른코스"));

        // when
        int totalSum = orderRepository.sumOrderCountByCourseIdAndStatuses(
                courseId, 
                List.of(OrderStatus.PENDING, OrderStatus.PAID, OrderStatus.CONFIRMED)
        );

        // then
        assertThat(totalSum).isEqualTo(2 + 3 + 5); // 10
    }

    @Test
    @DisplayName("상태별 좌석 합계 조회 - 조건에 맞는 주문이 없으면 0을 반환한다")
    void sumOrderCountByCourseIdAndStatuses_returnZero() {
        // given
        UUID courseId = UUID.randomUUID();
        orderRepository.save(Order.register(UUID.randomUUID(), UUID.randomUUID(), 10, BigInteger.valueOf(10000), "다른코스"));

        // when
        int totalSum = orderRepository.sumOrderCountByCourseIdAndStatuses(
                courseId, 
                List.of(OrderStatus.PAID)
        );

        // then
        assertThat(totalSum).isZero();
    }

    @Test
    @DisplayName("환불 가능 주문 조회 - 특정 코스의 PAID, CONFIRMED 주문만 조회한다")
    void findRefundableOrdersByCourseId_success() {
        // given
        UUID courseId = UUID.randomUUID();
        
        // 1. PAID (포함)
        Order paidOrder = Order.register(UUID.randomUUID(), courseId, 1, BigInteger.valueOf(10000), "테스트1");
        paidOrder.completePayment();
        orderRepository.save(paidOrder);
        
        // 2. CONFIRMED (포함)
        Order confirmedOrder = Order.register(UUID.randomUUID(), courseId, 1, BigInteger.valueOf(10000), "테스트2");
        ReflectionTestUtils.setField(confirmedOrder, "status", OrderStatus.CONFIRMED);
        orderRepository.save(confirmedOrder);
        
        // 3. PENDING (제외)
        orderRepository.save(Order.register(UUID.randomUUID(), courseId, 1, BigInteger.valueOf(10000), "테스트3"));
        
        // 4. CANCELLED (제외)
        Order cancelledOrder = Order.register(UUID.randomUUID(), courseId, 1, BigInteger.valueOf(10000), "테스트4");
        ReflectionTestUtils.setField(cancelledOrder, "status", OrderStatus.CANCELLED);
        orderRepository.save(cancelledOrder);
        
        // 5. 다른 코스 주문 (제외)
        Order otherCourseOrder = Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "다른코스");
        otherCourseOrder.completePayment();
        orderRepository.save(otherCourseOrder);

        // when
        List<Order> result = orderRepository.findRefundableOrdersByCourseId(courseId);

        // then
        assertThat(result).hasSize(2);
        assertThat(result).extracting(Order::getId)
                .containsExactlyInAnyOrder(paidOrder.getId(), confirmedOrder.getId());
    }

    @Test
    @DisplayName("중복 예약 조회 - 시간대가 겹치는 유효한 주문이 있으면 true를 반환한다")
    void hasOverlappingReservation_returnsTrue() {
        // given
        UUID memberId = UUID.randomUUID();
        LocalDateTime baseTime = LocalDateTime.now().withNano(0);
        
        // 14:00 ~ 16:00 코스 예약 존재
        Course course = courseRepository.save(openCourse(baseTime.withHour(14), baseTime.withHour(16)));
        orderRepository.save(Order.register(memberId, course.getId(), 1, BigInteger.valueOf(10000), "기존 예약"));

        // when: 15:00 ~ 17:00 시간대로 중복 조회 (1시간 겹침)
        boolean hasOverlap = orderRepository.hasOverlappingReservation(
                memberId, UUID.randomUUID(), baseTime.withHour(15), baseTime.withHour(17));

        // then
        assertThat(hasOverlap).isTrue();
    }

    @Test
    @DisplayName("중복 예약 조회 - 시간대가 겹치더라도 주문 상태가 유효하지 않으면 false를 반환한다")
    void hasOverlappingReservation_invalidStatus_returnsFalse() {
        // given
        UUID memberId = UUID.randomUUID();
        LocalDateTime baseTime = LocalDateTime.now().withNano(0);
        
        Course course = courseRepository.save(openCourse(baseTime.withHour(14), baseTime.withHour(16)));
        Order cancelledOrder = Order.register(memberId, course.getId(), 1, BigInteger.valueOf(10000), "취소된 예약");
        ReflectionTestUtils.setField(cancelledOrder, "status", OrderStatus.CANCELLED);
        orderRepository.save(cancelledOrder);

        // when: 14:00 ~ 16:00 동일 시간대 조회
        boolean hasOverlap = orderRepository.hasOverlappingReservation(
                memberId, UUID.randomUUID(), baseTime.withHour(14), baseTime.withHour(16));

        // then
        assertThat(hasOverlap).isFalse();
    }

    @Test
    @DisplayName("중복 예약 조회 - 시간대가 맞닿아 있는 경우(겹치지 않음) false를 반환한다")
    void hasOverlappingReservation_boundary_returnsFalse() {
        // given
        UUID memberId = UUID.randomUUID();
        LocalDateTime baseTime = LocalDateTime.now().withNano(0);
        
        // 14:00 ~ 16:00 코스 예약 존재
        Course course = courseRepository.save(openCourse(baseTime.withHour(14), baseTime.withHour(16)));
        orderRepository.save(Order.register(memberId, course.getId(), 1, BigInteger.valueOf(10000), "기존 예약"));

        // when: 16:00 ~ 18:00 시간대로 조회 (종료 시간과 시작 시간이 같음)
        boolean hasOverlap = orderRepository.hasOverlappingReservation(
                memberId, UUID.randomUUID(), baseTime.withHour(16), baseTime.withHour(18));

        // then
        assertThat(hasOverlap).isFalse();
    }

    @Test
    @DisplayName("중복 예약 조회 - 다른 회원의 예약인 경우 false를 반환한다")
    void hasOverlappingReservation_otherMember_returnsFalse() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID otherMemberId = UUID.randomUUID();
        LocalDateTime baseTime = LocalDateTime.now().withNano(0);
        
        Course course = courseRepository.save(openCourse(baseTime.withHour(14), baseTime.withHour(16)));
        orderRepository.save(Order.register(otherMemberId, course.getId(), 1, BigInteger.valueOf(10000), "다른 회원 예약"));

        // when: 동일 시간대 조회
        boolean hasOverlap = orderRepository.hasOverlappingReservation(
                memberId, UUID.randomUUID(), baseTime.withHour(14), baseTime.withHour(16));

        // then
        assertThat(hasOverlap).isFalse();
    }

    private Order confirmedOrder(UUID memberId, UUID courseId) {
        Order order = Order.register(memberId, courseId, 1, BigInteger.valueOf(50000), "테스트 강의");
        ReflectionTestUtils.setField(order, "status", OrderStatus.CONFIRMED);
        return order;
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
