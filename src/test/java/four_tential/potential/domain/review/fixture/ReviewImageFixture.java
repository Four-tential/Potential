package four_tential.potential.domain.review.fixture;

import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review_image.ReviewImage;

public class ReviewImageFixture {

    public static final String DEFAULT_IMAGE_URL = "https://cdn.example.com/images/review/sample.jpg";

    public static ReviewImage defaultReviewImage() {
        Review review = ReviewFixture.defaultReview();
        return ReviewImage.register(review, DEFAULT_IMAGE_URL);
    }

    public static ReviewImage reviewImageWithUrl(String imageUrl) {
        Review review = ReviewFixture.defaultReview();
        return ReviewImage.register(review, imageUrl);
    }
}