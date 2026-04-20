package four_tential.potential.domain.payment.port;

/**
 * PortOne 결제 취소 요청 파라미터
 *
 * amount                   : 이번에 취소할 금액
 * currentCancellableAmount : 현재 취소 가능한 잔여 금액
 */
public record PaymentGatewayRequest(String pgKey, Long amount, Long currentCancellableAmount, String reason) {

    /** 전액 취소 — 잔여 취소 가능 금액이 곧 amount 와 같다 */
    public static PaymentGatewayRequest of( String pgKey, Long amount, String reason) {
        return new PaymentGatewayRequest(pgKey, amount, amount, reason);
    }

    /** 부분 취소 — 취소할 금액과 현재 취소 가능한 잔여 금액을 분리해서 넘긴다 */
    public static PaymentGatewayRequest ofPartial(
            String pgKey,
            Long amount,
            Long currentCancellableAmount,
            String reason
    ) {
        return new PaymentGatewayRequest(pgKey, amount, currentCancellableAmount, reason);
    }
}
