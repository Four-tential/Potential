package four_tential.potential.domain.review.fixture;

import four_tential.potential.domain.review.review_like.ReviewLike;

import java.util.UUID;

public class ReviewLikeFixture {

    public static final UUID DEFAULT_REVIEW_ID = UUID.randomUUID();
    public static final UUID DEFAULT_MEMBER_ID = UUID.randomUUID();

    public static ReviewLike defaultReviewLike() {
        return ReviewLike.register(DEFAULT_REVIEW_ID, DEFAULT_MEMBER_ID);
    }

    public static ReviewLike reviewLikeWithMemberId(UUID memberId) {
        return ReviewLike.register(DEFAULT_REVIEW_ID, memberId);
    }
}