package four_tential.potential.domain.review.review_image;

import four_tential.potential.domain.review.fixture.ReviewFixture;
import four_tential.potential.domain.review.fixture.ReviewImageFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ReviewImageTest {

    @Nested
    @DisplayName("register() - 후기 이미지 생성")
    class RegisterTest {

        @Test
        @DisplayName("정상 생성 시 review 와 imageUrl 이 올바르게 설정된다")
        void register_success() {
            ReviewImage image = ReviewImageFixture.defaultReviewImage();

            assertThat(image.getReview()).isNotNull();
            assertThat(image.getImageUrl()).isEqualTo(ReviewImageFixture.DEFAULT_IMAGE_URL);
        }

        @Test
        @DisplayName("생성된 후기 이미지는 id 가 null 이다")
        void register_initialId_isNull() {
            ReviewImage image = ReviewImageFixture.defaultReviewImage();

            assertThat(image.getId()).isNull();
        }

        @Test
        @DisplayName("연결된 review 의 rating 이 올바르게 참조된다")
        void register_reviewRatingIsAccessible() {
            ReviewImage image = ReviewImageFixture.defaultReviewImage();

            assertThat(image.getReview().getRating()).isEqualTo(ReviewFixture.DEFAULT_RATING);
        }

        @Test
        @DisplayName("imageUrl 을 지정하면 해당 imageUrl 이 설정된다")
        void register_withCustomImageUrl() {
            String customUrl = "https://cdn.example.com/images/review/custom.png";
            ReviewImage image = ReviewImageFixture.reviewImageWithUrl(customUrl);

            assertThat(image.getImageUrl()).isEqualTo(customUrl);
        }

        @Test
        @DisplayName("서로 다른 imageUrl 로 생성한 이미지는 각각 독립적이다")
        void register_multipleImages_areIndependent() {
            ReviewImage imageA = ReviewImageFixture.reviewImageWithUrl("https://cdn.example.com/a.jpg");
            ReviewImage imageB = ReviewImageFixture.reviewImageWithUrl("https://cdn.example.com/b.jpg");

            assertThat(imageA.getImageUrl()).isNotEqualTo(imageB.getImageUrl());
        }
    }
}