package four_tential.potential.presentation.course.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CourseListInstructorInfo(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID memberId,
        @Schema(example = "김강사") String name,
        @Schema(example = "https://cdn.example.com/instructor-image/3fa85f64-5717-4562-b3fc-2c963f66afa6/profile.jpg") String profileImageUrl
) {
}
