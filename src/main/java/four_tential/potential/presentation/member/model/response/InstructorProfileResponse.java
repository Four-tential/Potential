package four_tential.potential.presentation.member.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record InstructorProfileResponse(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID memberId,
        @Schema(example = "김강사") String name,
        @Schema(example = "https://cdn.example.com/instructor-image/3fa85f64-5717-4562-b3fc-2c963f66afa6/profile.jpg") String profileImageUrl,
        @Schema(example = "YOGA") String categoryCode,
        @Schema(example = "요가") String categoryName,
        @Schema(example = "10년 경력의 요가 강사입니다.") String content,
        @Schema(example = "5") long courseCount,
        @Schema(example = "4.8") double averageRating,
        @Schema(example = "120") long totalStudentCount
) {
}
