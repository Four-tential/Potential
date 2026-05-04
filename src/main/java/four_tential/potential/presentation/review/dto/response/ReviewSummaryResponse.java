package four_tential.potential.presentation.review.dto.response;

import java.util.UUID;

public record ReviewSummaryResponse(
        UUID courseId,
        String summary
) {
    public static ReviewSummaryResponse of(UUID courseId, String summary) {
        return new ReviewSummaryResponse(courseId, summary);
    }
}