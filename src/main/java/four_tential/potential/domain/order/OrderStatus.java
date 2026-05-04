package four_tential.potential.domain.order;

import lombok.Getter;

@Getter
public enum OrderStatus {
    PENDING("결제 대기"),
    PAID("결제 완료"),
    CONFIRMED("결제 확정"),
    CANCELLED("주문 취소"),
    EXPIRED("결제 만료"),
    REFUND_PENDING("환불 대기");

    private final String description;

    OrderStatus(String description) {
        this.description = description;
    }
}
