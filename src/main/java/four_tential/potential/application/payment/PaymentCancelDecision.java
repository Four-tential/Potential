package four_tential.potential.application.payment;

/**
 * 웹훅 처리 결과로 PortOne 결제 취소가 필요한지 알려주는 recode
 * DB 트랜잭션 안에서는 Payment 상태만 변경하고
 * 실제 PortOne 취소 API 호출은 트랜잭션 밖에서 수행하기 위해 이 결과 객체를 사용
 */
public record PaymentCancelDecision(boolean cancelRequired, String pgKey, Long cancelAmount, String cancelReason) {

    // 추가로 PortOne 취소 요청이 필요 없는 정상 처리 결과를 만든다
    public static PaymentCancelDecision none() {
        return new PaymentCancelDecision(false, null, 0L, null);
    }

    // 서버 검증 결과 결제를 받을 수 없을 때 PortOne 취소에 필요한 정보를 담는다
    public static PaymentCancelDecision cancel(String pgKey, Long amount, String reason) {
        return new PaymentCancelDecision(true, pgKey, amount, reason);
    }
}
