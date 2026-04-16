package four_tential.potential.domain.review.review;

import four_tential.potential.domain.review.fixture.ReviewFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewTest {

    @Nested
    @DisplayName("register() - 후기 생성")
    class RegisterTest {

        @Test
        @DisplayName("정상 생성 시 memberId, courseId, orderId, rating, content 가 올바르게 설정된다")
        void register_success() {
            Review review = ReviewFixture.defaultReview();

            assertThat(review.getMemberId()).isEqualTo(ReviewFixture.DEFAULT_MEMBER_ID);
            assertThat(review.getCourseId()).isEqualTo(ReviewFixture.DEFAULT_COURSE_ID);
            assertThat(review.getOrderId()).isEqualTo(ReviewFixture.DEFAULT_ORDER_ID);
            assertThat(review.getRating()).isEqualTo(ReviewFixture.DEFAULT_RATING);
            assertThat(review.getContent()).isEqualTo(ReviewFixture.DEFAULT_CONTENT);
        }

        @Test
        @DisplayName("생성된 후기는 id 가 null 이다")
        void register_initialId_isNull() {
            Review review = ReviewFixture.defaultReview();

            assertThat(review.getId()).isNull();
        }

        @Test
        @DisplayName("rating 1점으로 생성 시 rating 이 1 이다")
        void register_withMinRating() {
            Review review = ReviewFixture.reviewWithRating(1);

            assertThat(review.getRating()).isEqualTo(1);
        }

        @Test
        @DisplayName("rating 5점으로 생성 시 rating 이 5 이다")
        void register_withMaxRating() {
            Review review = ReviewFixture.reviewWithRating(5);

            assertThat(review.getRating()).isEqualTo(5);
        }

        @Test
        @DisplayName("content 를 지정하면 해당 content 가 설정된다")
        void register_withContent() {
            String content = "강사님이 친절하고 내용이 알찼어요.";
            Review review = ReviewFixture.reviewWithContent(content);

            assertThat(review.getContent()).isEqualTo(content);
        }
    }

    @Nested
    @DisplayName("update() - 후기 수정")
    class UpdateTest {

        @Test
        @DisplayName("update() 호출 시 rating 이 변경된다")
        void update_ratingChanged() {
            // given
            Review review = ReviewFixture.defaultReview();

            // when
            review.update(3, review.getContent());

            // then
            assertThat(review.getRating()).isEqualTo(3);
        }

        @Test
        @DisplayName("update() 호출 시 content 가 변경된다")
        void update_contentChanged() {
            // given
            Review review = ReviewFixture.defaultReview();
            String newContent = "생각보다 별로였어요.";

            // when
            review.update(review.getRating(), newContent);

            // then
            assertThat(review.getContent()).isEqualTo(newContent);
        }

        @Test
        @DisplayName("update() 호출 시 rating 과 content 가 동시에 변경된다")
        void update_ratingAndContentChanged() {
            // given
            Review review = ReviewFixture.defaultReview();
            String newContent = "다시 생각해보니 꽤 좋았어요.";

            // when
            review.update(4, newContent);

            // then
            assertThat(review.getRating()).isEqualTo(4);
            assertThat(review.getContent()).isEqualTo(newContent);
        }

        @Test
        @DisplayName("update() 호출 전후 memberId 와 courseId 는 변경되지 않는다")
        void update_doesNotChangeMemberIdAndCourseId() {
            // given
            Review review = ReviewFixture.defaultReview();

            // when
            review.update(2, "수정된 내용");

            // then
            assertThat(review.getMemberId()).isEqualTo(ReviewFixture.DEFAULT_MEMBER_ID);
            assertThat(review.getCourseId()).isEqualTo(ReviewFixture.DEFAULT_COURSE_ID);
        }
    }
}