package four_tential.potential.domain.payment.enums;

public enum CourseCancelOutboxStatus {

    PENDING,  // Job1 미처리
    DONE,     // Job1 처리 완료 (refund_task 생성됨)
    FAILED    // Job1 처리 실패
}
