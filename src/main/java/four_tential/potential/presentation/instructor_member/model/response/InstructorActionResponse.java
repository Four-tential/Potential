package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;
import java.util.UUID;

public record InstructorActionResponse(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID memberId,
        @Schema(example = "APPROVED") InstructorMemberStatus status,
        @Schema(example = "2025-06-03T14:00:00") LocalDateTime respondedAt
) {
    public static InstructorActionResponse register(InstructorMember instructorMember) {
        return new InstructorActionResponse(
                instructorMember.getMemberId(),
                instructorMember.getStatus(),
                instructorMember.getRespondedAt()
        );
    }
}
