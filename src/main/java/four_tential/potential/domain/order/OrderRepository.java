package four_tential.potential.domain.order;

import four_tential.potential.domain.course.course.CourseStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, OrderRepositoryCustom {
    boolean existsByMemberIdAndCourseIdAndStatus(UUID memberId, UUID courseId, OrderStatus orderStatus);

    @Query("""
            SELECT COUNT(o) > 0
            FROM Order o
            JOIN Course c ON c.id = o.courseId
            WHERE o.memberId = :memberId
              AND o.status IN :orderStatuses
              AND c.status IN :courseStatuses
              AND c.endAt > :now
            """)
    boolean existsActiveEnrollment(
            @Param("memberId") UUID memberId,
            @Param("orderStatuses") Collection<OrderStatus> orderStatuses,
            @Param("courseStatuses") Collection<CourseStatus> courseStatuses,
            @Param("now") LocalDateTime now
    );

    Slice<Order> findAllByStatusAndExpireAtBefore(OrderStatus status, LocalDateTime now, Pageable pageable);
}
