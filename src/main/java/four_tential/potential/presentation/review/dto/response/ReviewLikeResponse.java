package four_tential.potential.presentation.review.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Getter
@Builder
public class ReviewLikeResponse {

    private UUID reviewId;
    private long likeCount;
    private boolean liked; // 요청자의 현재 좋아요 상태

    public static ReviewLikeResponse of(UUID reviewId, long likeCount, boolean liked) {
        return ReviewLikeResponse.builder()
                .reviewId(reviewId)
                .likeCount(likeCount)
                .liked(liked)
                .build();
    }
}