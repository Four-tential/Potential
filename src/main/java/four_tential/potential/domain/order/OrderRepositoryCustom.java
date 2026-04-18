package four_tential.potential.domain.order;

import four_tential.potential.domain.course.course.CourseStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryCustom {
    Optional<Order> findOrderDetailsById(UUID orderId, UUID memberId);
    Page<Order> findMyOrders(UUID memberId, Pageable pageable);

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
}
