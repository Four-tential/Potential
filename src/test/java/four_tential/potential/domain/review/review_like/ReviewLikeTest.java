package four_tential.potential.domain.review.review_like;

import four_tential.potential.domain.review.fixture.ReviewLikeFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewLikeTest {

    @Nested
    @DisplayName("register() - 후기 좋아요 생성")
    class RegisterTest {

        @Test
        @DisplayName("정상 생성 시 reviewId 와 memberId 가 올바르게 설정된다")
        void register_success() {
            ReviewLike like = ReviewLikeFixture.defaultReviewLike();

            assertThat(like.getReviewId()).isEqualTo(ReviewLikeFixture.DEFAULT_REVIEW_ID);
            assertThat(like.getMemberId()).isEqualTo(ReviewLikeFixture.DEFAULT_MEMBER_ID);
        }

        @Test
        @DisplayName("생성된 좋아요는 id 가 null 이다")
        void register_initialId_isNull() {
            ReviewLike like = ReviewLikeFixture.defaultReviewLike();

            assertThat(like.getId()).isNull();
        }

        @Test
        @DisplayName("서로 다른 memberId 로 생성한 좋아요는 각각 독립적이다")
        void register_multipleMembersLike_areIndependent() {
            // given
            UUID memberIdA = UUID.randomUUID();
            UUID memberIdB = UUID.randomUUID();

            // when
            ReviewLike likeA = ReviewLikeFixture.reviewLikeWithMemberId(memberIdA);
            ReviewLike likeB = ReviewLikeFixture.reviewLikeWithMemberId(memberIdB);

            // then
            assertThat(likeA.getMemberId()).isNotEqualTo(likeB.getMemberId());
            assertThat(likeA.getReviewId()).isEqualTo(likeB.getReviewId());
        }

        @Test
        @DisplayName("memberId 를 지정하면 해당 memberId 가 설정된다")
        void register_withCustomMemberId() {
            UUID customMemberId = UUID.randomUUID();
            ReviewLike like = ReviewLikeFixture.reviewLikeWithMemberId(customMemberId);

            assertThat(like.getMemberId()).isEqualTo(customMemberId);
        }
    }
}