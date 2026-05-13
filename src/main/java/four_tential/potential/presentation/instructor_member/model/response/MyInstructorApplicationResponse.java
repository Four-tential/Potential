package four_tential.potential.presentation.instructor_member.model.response;

import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.domain.member.instructor_member.MyInstructorApplicationResult;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

public record MyInstructorApplicationResponse(
        @Schema(example = "YOGA") String categoryCode,
        @Schema(example = "요가") String categoryName,
        @Schema(example = "10년 경력의 요가 강사입니다.") String content,
        @Schema(example = "https://cdn.example.com/instructor-image/3fa85f64/cert.jpg") String imageUrl,
        @Schema(example = "PENDING") InstructorMemberStatus status,
        @Schema(example = "포트폴리오 정보가 부족합니다.") String rejectReason,
        @Schema(example = "2025-06-01T10:00:00") LocalDateTime appliedAt,
        @Schema(example = "2025-06-03T14:00:00") LocalDateTime respondedAt
) {
    public static MyInstructorApplicationResponse register(MyInstructorApplicationResult result) {
        return new MyInstructorApplicationResponse(
                result.categoryCode(),
                result.categoryName(),
                result.content(),
                result.imageUrl(),
                result.status(),
                result.rejectReason(),
                result.appliedAt(),
                result.respondedAt()
        );
    }
}
