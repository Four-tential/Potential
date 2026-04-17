package four_tential.potential.domain.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.List;

public interface OrderRepository extends JpaRepository<Order, UUID>, OrderRepositoryCustom {
    boolean existsByMemberIdAndCourseIdAndStatus(UUID memberId, UUID courseId, OrderStatus orderStatus);

    List<Order> findAllByStatusAndExpireAtBefore(OrderStatus status, LocalDateTime now);
}
