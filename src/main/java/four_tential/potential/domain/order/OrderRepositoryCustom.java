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
}
