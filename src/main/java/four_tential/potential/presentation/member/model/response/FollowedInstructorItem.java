package four_tential.potential.presentation.member.model.response;

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
    public static FollowedInstructorItem register(
            UUID memberId,
            String name,
            String profileImageUrl,
            String categoryCode,
            String categoryName,
            Long courseCount,
            Double averageRating,
            LocalDateTime followedAt
    ) {
        return new FollowedInstructorItem(
                memberId,
                name,
                profileImageUrl,
                categoryCode,
                categoryName,
                courseCount,
                averageRating,
                followedAt
        );
    }
}
