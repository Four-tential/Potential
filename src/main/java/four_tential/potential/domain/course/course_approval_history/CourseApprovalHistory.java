package four_tential.potential.domain.course.course_approval_history;

import four_tential.potential.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "course_approval_histories")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class CourseApprovalHistory extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "course_id", nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false, length = 10)
    private CourseApprovalAction action;

    @Column(name = "reject_reason", updatable = false)
    private String rejectReason;

    public static CourseApprovalHistory register(UUID courseId, CourseApprovalAction action, String rejectReason) {
        CourseApprovalHistory history = new CourseApprovalHistory();
        history.courseId = courseId;
        history.action = action;
        history.rejectReason = rejectReason;
        return history;
    }
}
