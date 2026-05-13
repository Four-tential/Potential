package four_tential.potential.presentation.course.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record CourseWishlistResponse(
        @Schema(example = "a1b2c3d4-e5f6-7890-abcd-ef1234567890") UUID courseId,
        @Schema(example = "true") boolean isWishlisted
) {
}
