package four_tential.potential.application.payment;

import java.util.UUID;

/**
 * 결제 생성 전에 서버가 계산한 결제 준비 값을 담는 application 계층 record
 * 주문 조회와 금액 계산이 끝난 뒤 PaymentService로 넘기기 위해 사용한다
 * 결제 생성 use case 내부 전달 객체이다
 */
public record PaymentCreateCommand(
        UUID orderId,
        UUID memberId,
        Long totalPrice,
        Long paidTotalPrice
) {
}
