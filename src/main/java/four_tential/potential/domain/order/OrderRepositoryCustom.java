package four_tential.potential.domain.order;

import four_tential.potential.domain.course.course.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryCustom {
    Optional<Order> findOrderDetailsById(UUID orderId, UUID memberId);
    Page<Order> findMyOrders(UUID memberId, Pageable pageable);

    /**
     * 특정 코스의 유효한 주문(PENDING, PAID, CONFIRMED)들의 총 좌석 수 합계를 조회
     */
    int sumOrderCountByCourseIdAndStatuses(UUID courseId, List<OrderStatus> statuses);

    // 특정 코스에서 CONFIRMED 상태인 수강생 명단 (출석 정보 포함)
    Page<CourseStudentQueryResult> findConfirmedStudentsByCourseId(UUID courseId, Pageable pageable);

    boolean existsActiveEnrollment(
            UUID memberId,
            Collection<OrderStatus> orderStatuses,
            Collection<CourseStatus> courseStatuses,
            LocalDateTime now
    );

    Long sumStudentCountByMemberInstructorIdAndStatusIn(
            UUID memberInstructorId,
            Collection<OrderStatus> statuses
    );

    List<Order> findRefundableOrdersByCourseId(UUID courseId);

    /**
     * 결제 완료(PAID) 상태이면서 환불 가능 기간(코스 시작 7일 전)이 지난 주문 목록 조회
     */
    List<Order> findPaidOrdersToConfirm(LocalDateTime now, Pageable pageable);

    /**
     * 특정 시간대에 이미 유효한(PENDING, PAID, CONFIRMED) 주문이 있는지 확인
     */
    boolean hasOverlappingReservation(UUID memberId, LocalDateTime startAt, LocalDateTime endAt);
}
