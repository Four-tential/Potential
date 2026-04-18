package four_tential.potential.presentation.member.model.response;

import java.util.UUID;

public record FollowResponse(
        UUID instructorId,
        boolean isFollowed
) {
    public static FollowResponse register(UUID instructorId, boolean isFollowed) {
        return new FollowResponse(instructorId, isFollowed);
    }
}
