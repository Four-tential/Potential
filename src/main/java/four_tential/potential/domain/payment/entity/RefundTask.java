package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.domain.payment.enums.RefundTaskStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "refund_task",
        indexes = {
                @Index(name = "idx_refund_task_status", columnList = "status"),
                @Index(name = "idx_refund_task_order_id", columnList = "order_id"),
                @Index(name = "idx_refund_task_course_id", columnList = "course_id")
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
    }

    public void markFailed(String reason) {
        this.status = RefundTaskStatus.FAILED;
        this.failReason = (reason != null && reason.length() > 500)
                ? reason.substring(0, 500) : reason;
    }
}
