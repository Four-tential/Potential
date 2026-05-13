package four_tential.potential.presentation.member.model.response;

import io.swagger.v3.oas.annotations.media.Schema;

import java.util.UUID;

public record FollowResponse(
        @Schema(example = "3fa85f64-5717-4562-b3fc-2c963f66afa6") UUID instructorId,
        @Schema(example = "true") boolean isFollowed
) {
    public static FollowResponse register(UUID instructorId, boolean isFollowed) {
        return new FollowResponse(instructorId, isFollowed);
    }
}
