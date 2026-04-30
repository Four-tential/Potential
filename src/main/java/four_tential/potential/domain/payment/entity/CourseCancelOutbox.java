package four_tential.potential.domain.payment.entity;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.domain.payment.enums.CourseCancelOutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(
        name = "course_cancel_outbox",
        indexes = {
                @Index(name = "idx_course_cancel_outbox_status", columnList = "status"),
                @Index(name = "idx_course_cancel_outbox_course_id", columnList = "course_id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CourseCancelOutbox extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "course_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private CourseCancelOutboxStatus status;

    @Column(name = "fail_reason", length = 500)
    private String failReason;


    /**
     * 코스 취소 시 생성. course.cancel() 과 같은 트랜잭션에서 호출해야 한다.
     */
    public static CourseCancelOutbox pending(UUID courseId) {
        CourseCancelOutbox outbox = new CourseCancelOutbox();
        outbox.courseId = courseId;
        outbox.status = CourseCancelOutboxStatus.PENDING;
        return outbox;
    }


    public void markDone() {
        this.status = CourseCancelOutboxStatus.DONE;
    }

    public void markFailed(String reason) {
        this.status = CourseCancelOutboxStatus.FAILED;
        this.failReason = (reason != null && reason.length() > 500)
                ? reason.substring(0, 500) : reason;
    }
}
