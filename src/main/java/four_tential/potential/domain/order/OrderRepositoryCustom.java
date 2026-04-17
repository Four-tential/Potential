package four_tential.potential.domain.order;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryCustom {
    Optional<Order> findOrderDetailsById(UUID orderId, UUID memberId);
    Page<Order> findMyOrders(UUID memberId, Pageable pageable);
}
