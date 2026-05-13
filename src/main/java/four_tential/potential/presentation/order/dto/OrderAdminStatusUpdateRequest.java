package four_tential.potential.presentation.order.dto;

import four_tential.potential.domain.order.OrderStatus;
import jakarta.validation.constraints.NotNull;

public record OrderAdminStatusUpdateRequest(
        @NotNull(message = "변경할 상태는 필수입니다")
        OrderStatus targetStatus,
        String reason
) {
}
