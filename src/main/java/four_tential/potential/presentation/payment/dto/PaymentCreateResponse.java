package four_tential.potential.presentation.payment.dto;

import four_tential.potential.domain.payment.entity.Payment;
import four_tential.potential.domain.payment.enums.PaymentPayWay;
import four_tential.potential.domain.payment.enums.PaymentStatus;

import java.time.LocalDateTime;
import java.util.UUID;

public record PaymentCreateResponse(
        UUID paymentId,
        UUID orderId,
        String pgKey,
        Long totalPrice,
        Long paidTotalPrice,
        PaymentPayWay payWay,
        PaymentStatus status,
        LocalDateTime createdAt
) {

    public static PaymentCreateResponse from(Payment payment) {
        return new PaymentCreateResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getPgKey(),
                payment.getTotalPrice(),
                payment.getPaidTotalPrice(),
                payment.getPayWay(),
                payment.getStatus(),
                payment.getCreatedAt()
        );
    }
}
