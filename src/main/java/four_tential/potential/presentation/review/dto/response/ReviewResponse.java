package four_tential.potential.presentation.review.dto.response;

import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review_image.ReviewImage;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public record ReviewResponse(
        UUID reviewId,
        UUID memberId,
        UUID courseId,
        int rating,
        String content,
        List<String> imageUrls,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    public static ReviewResponse of(Review review, List<ReviewImage> images) {
        return new ReviewResponse(
                review.getId(),
                review.getMemberId(),
                review.getCourseId(),
                review.getRating(),
                review.getContent(),
                images.stream().map(ReviewImage::getImageUrl).toList(),
                review.getCreatedAt(),
                review.getUpdatedAt()
        );
    }
}