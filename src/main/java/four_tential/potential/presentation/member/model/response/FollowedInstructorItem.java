package four_tential.potential.presentation.member.model.response;

import four_tential.potential.domain.member.follow.FollowQueryResult;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowedInstructorItem(
        UUID memberId,
        String name,
        String profileImageUrl,
        String categoryCode,
        String categoryName,
        Long courseCount,
        Double averageRating,
        LocalDateTime followedAt
) {
    public static FollowedInstructorItem register(FollowQueryResult result) {
        return new FollowedInstructorItem(
                result.memberId(),
                result.name(),
                result.profileImageUrl(),
                result.categoryCode(),
                result.categoryName(),
                result.courseCount(),
                result.averageRating(),
                result.followedAt()
        );
    }
}
