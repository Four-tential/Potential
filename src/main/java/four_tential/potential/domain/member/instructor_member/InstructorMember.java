package four_tential.potential.domain.member.instructor_member;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.common.exception.ServiceErrorException;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_INVALID_STATUS_TRANSITION_TO_APPROVE;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_INVALID_STATUS_TRANSITION_TO_REJECT;

@Getter
@Entity
@Table(name = "instructor_members")
@NoArgsConstructor(access = lombok.AccessLevel.PROTECTED)
public class InstructorMember extends BaseTimeEntity {
    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Column(name = "category_code", nullable = false, length = 20)
    private String categoryCode;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private InstructorMemberStatus status;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "image_url", nullable = false, length = 300)
    private String imageUrl;

    @Column(name = "reject_reason", columnDefinition = "TEXT")
    private String rejectReason;

    @Column(name = "approved_at")
    private LocalDateTime approvedAt;

    @Column(name = "responsed_at")
    private LocalDateTime responsedAt;

    public static InstructorMember apply(UUID memberId, String categoryCode,
                                         String content, String imageUrl) {
        InstructorMember instructorMember = new InstructorMember();
        instructorMember.memberId = memberId;
        instructorMember.categoryCode = categoryCode;
        instructorMember.content = content;
        instructorMember.imageUrl = imageUrl;
        instructorMember.status = InstructorMemberStatus.PENDING;
        return instructorMember;
    }

    public void approve() {
        if (this.status != InstructorMemberStatus.PENDING) {
            throw new ServiceErrorException(ERR_INVALID_STATUS_TRANSITION_TO_APPROVE);
        }
        this.status = InstructorMemberStatus.APPROVED;
        this.approvedAt = LocalDateTime.now();
        this.responsedAt = LocalDateTime.now();
    }

    public void reject(String rejectReason) {
        if (this.status != InstructorMemberStatus.PENDING) {
            throw new ServiceErrorException(ERR_INVALID_STATUS_TRANSITION_TO_REJECT);
        }
        this.status = InstructorMemberStatus.REJECTED;
        this.rejectReason = rejectReason;
        this.responsedAt = LocalDateTime.now();
    }


}
