package four_tential.potential.domain.payment.enums;

public enum RefundTaskStatus {
    PENDING,  // Job2 미처리
    DONE,     // 환불 완료
    RETRY_PENDING, // next_retry_at 이후 재시도
    FAILED    // Job2 처리 실패
}
