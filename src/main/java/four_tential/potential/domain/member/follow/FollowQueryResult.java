package four_tential.potential.domain.member.follow;

import java.time.LocalDateTime;
import java.util.UUID;

public record FollowQueryResult(
        UUID memberId,
        String name,
        String profileImageUrl,
        String categoryCode,
        String categoryName,
        Long courseCount,
        Double averageRating,
        LocalDateTime followedAt
) {
}
