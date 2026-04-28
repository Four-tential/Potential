package four_tential.potential.domain.order;

import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<Order, UUID>, OrderRepositoryCustom {
    boolean existsByMemberIdAndCourseIdAndStatus(UUID memberId, UUID courseId, OrderStatus orderStatus);

    boolean existsByMemberIdAndCourseIdAndStatusIn(UUID memberId, UUID courseId, Collection<OrderStatus> statuses);

    Slice<Order> findAllByStatusAndExpireAtBefore(OrderStatus status, LocalDateTime now, Pageable pageable);
}
