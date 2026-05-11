package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(
        name = "refund_task",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_refund_task_order_id", columnNames = {"order_id"})
        },
        indexes = {
                @Index(name = "idx_refund_task_status", columnList = "status"),
                @Index(name = "idx_refund_task_course_id", columnList = "course_id"),
                @Index(name = "idx_refund_task_next_retry_at", columnList = "next_retry_at")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefundTask extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "course_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(name = "payment_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID paymentId;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private RefundTaskStatus status;

    @Column(name = "next_retry_at")
    private LocalDateTime nextRetryAt;

    @Column(name = "fail_reason", length = 500)
    private String failReason;

    /**
     * Job1에서 REFUND_PENDING 주문을 보고 생성한다.
     */
    public static RefundTask pending(
            UUID courseId,
            UUID orderId,
            UUID paymentId,
            UUID memberId
    ) {
        RefundTask task = new RefundTask();
        task.courseId = courseId;
        task.orderId = orderId;
        task.paymentId = paymentId;
        task.memberId = memberId;
        task.status = RefundTaskStatus.PENDING;
        return task;
    }

    public void markDone() {
        this.status = RefundTaskStatus.DONE;
        this.failReason = null;
        this.nextRetryAt = null;
    }

    // 최종 실패 - 재시도해도 안 되는 경우
    public void markFailed(String reason) {
        this.status = RefundTaskStatus.FAILED;
        this.failReason = (reason != null && reason.length() > 500)
                ? reason.substring(0, 500) : reason;
        this.nextRetryAt = null;
    }

    // 일시적 실패 - 나중에 재시도할 대상
    public void markRetryPending(LocalDateTime retryAt, String reason) {
        this.status = RefundTaskStatus.RETRY_PENDING;
        this.nextRetryAt = retryAt;
        this.failReason = (reason != null && reason.length() > 500)
                ? reason.substring(0, 500) : reason;
    }
}
