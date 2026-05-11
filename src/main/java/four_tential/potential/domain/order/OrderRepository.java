package four_tential.potential.domain.order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, OrderRepositoryCustom {
    boolean existsByMemberIdAndCourseIdAndStatus(UUID memberId, UUID courseId, OrderStatus orderStatus);

    boolean existsByMemberIdAndCourseIdAndStatusIn(UUID memberId, UUID courseId, Collection<OrderStatus> statuses);

    Slice<Order> findAllByStatusAndExpireAtBefore(OrderStatus status, LocalDateTime now, Pageable pageable);

    @Modifying
    @Query("delete from Order o where o.memberId in :memberIds")
    void deleteByMemberIdIn(@Param("memberIds") Collection<UUID> memberIds);

    @Modifying
    @Query("delete from Order o where o.courseId in :courseIds")
    void deleteByCourseIdIn(@Param("courseIds") Collection<UUID> courseIds);
}
