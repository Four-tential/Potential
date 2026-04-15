package four_tential.potential.domain.order;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepositoryCustom {
    Optional<Order> findOrderDetailsById(UUID orderId, UUID memberId);
}
