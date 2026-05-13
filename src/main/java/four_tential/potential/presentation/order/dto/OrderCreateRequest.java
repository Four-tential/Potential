package four_tential.potential.presentation.order.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record OrderCreateRequest(
        @NotNull(message = "코스 ID는 필수입니다")
        UUID courseId,

        @Min(value = 1, message = "주문 수량은 최소 1개 이상이어야 합니다")
        int orderCount
) {
}
