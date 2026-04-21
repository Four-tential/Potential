package four_tential.potential.presentation.order.dto;

import jakarta.validation.constraints.Min;

/**
 * 수강생이 주문 취소 시 보내는 요청이다.
 * cancelCount는 현재 가진 수강권 중 이번에 취소할 수량이다.
 */
public record OrderCancelRequest(
        @Min(value = 1, message = "취소 수량은 1 이상이어야 합니다")
        int cancelCount
) {
}
