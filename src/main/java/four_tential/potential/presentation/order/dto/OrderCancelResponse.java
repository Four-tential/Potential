package four_tential.potential.presentation.order.dto;

import four_tential.potential.domain.order.Order;

import java.time.LocalDateTime;
import java.util.UUID;

public record OrderCancelResponse(
        UUID orderId,
        String status,
        LocalDateTime cancelledAt
) {
    public static OrderCancelResponse from(Order order) {
        return new OrderCancelResponse(
                order.getId(),
                order.getStatus().name(),
                order.getCancelledAt()
        );
    }
}
