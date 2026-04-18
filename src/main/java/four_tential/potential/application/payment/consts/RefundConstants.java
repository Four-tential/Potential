package four_tential.potential.application.payment.consts;

public final class RefundConstants {
    // 환불 가능 기준일
    public static final int REFUND_DEADLINE_DAYS = 7;

    // 환불 가능 정책 문구
    public static final String REFUND_POLICY_REFUNDABLE = "7일 전 취소 · 전액 환불";

    // 환불 불가 정책 문구
    public static final String REFUND_POLICY_NOT_REFUNDABLE = "7일 이내 취소 · 환불 불가";

    private RefundConstants() {
    }
}
