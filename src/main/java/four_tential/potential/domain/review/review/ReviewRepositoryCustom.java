package four_tential.potential.domain.review.review;

import java.util.UUID;

public interface ReviewRepositoryCustom {
    Double findAverageRatingByMemberInstructorId(UUID memberInstructorId);
}
