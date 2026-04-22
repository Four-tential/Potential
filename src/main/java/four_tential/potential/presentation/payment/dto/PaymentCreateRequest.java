package four_tential.potential.presentation.payment.dto;

import four_tential.potential.domain.payment.enums.PaymentPayWay;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record PaymentCreateRequest(
        @NotNull(message = "주문 ID는 필수입니다.")
        UUID orderId,

        @NotNull(message = "결제 수단은 필수입니다.")
        PaymentPayWay payWay
) {
}
