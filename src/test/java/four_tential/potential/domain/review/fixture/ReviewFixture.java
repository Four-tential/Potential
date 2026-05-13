package four_tential.potential.domain.review.fixture;

import four_tential.potential.domain.review.review.Review;

import java.util.UUID;

public class ReviewFixture {

    public static final UUID DEFAULT_MEMBER_ID = UUID.randomUUID();
    public static final UUID DEFAULT_COURSE_ID = UUID.randomUUID();
    public static final UUID DEFAULT_ORDER_ID = UUID.randomUUID();
    public static final int DEFAULT_RATING = 5;
    public static final String DEFAULT_CONTENT = "정말 유익한 클래스였습니다.";

    public static Review defaultReview() {
        return Review.register(DEFAULT_MEMBER_ID, DEFAULT_COURSE_ID, DEFAULT_ORDER_ID, DEFAULT_RATING, DEFAULT_CONTENT);
    }

    public static Review reviewWithRating(int rating) {
        return Review.register(DEFAULT_MEMBER_ID, DEFAULT_COURSE_ID, DEFAULT_ORDER_ID, rating, DEFAULT_CONTENT);
    }

    public static Review reviewWithContent(String content) {
        return Review.register(DEFAULT_MEMBER_ID, DEFAULT_COURSE_ID, DEFAULT_ORDER_ID, DEFAULT_RATING, content);
    }
}