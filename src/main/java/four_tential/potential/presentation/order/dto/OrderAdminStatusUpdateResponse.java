package four_tential.potential.presentation.order.dto;

import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderStatus;
import java.util.UUID;

public record OrderAdminStatusUpdateResponse(
        UUID orderId,
        OrderStatus previousStatus,
        OrderStatus currentStatus
) {
    public static OrderAdminStatusUpdateResponse of(Order order, OrderStatus previousStatus) {
        return new OrderAdminStatusUpdateResponse(
                order.getId(),
                previousStatus,
                order.getStatus()
        );
    }
}
