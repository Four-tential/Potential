package four_tential.potential.presentation.review.dto.response;

import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review_image.ReviewImage;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonPOJOBuilder;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Getter
@Builder
@JsonTypeInfo(use = JsonTypeInfo.Id.NONE)  // Redis 캐싱 시 타입 정보(@class) 포함 방지
@JsonDeserialize(builder = ReviewResponse.ReviewResponseBuilder.class)
public class ReviewResponse {

    private UUID reviewId;
    private UUID memberId;
    private UUID courseId;
    private int rating;
    private String content;
    private List<String> imageUrls;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    @JsonPOJOBuilder(withPrefix = "")
    public static class ReviewResponseBuilder {}

    public static ReviewResponse of(Review review, List<ReviewImage> images) {
        return ReviewResponse.builder()
                .reviewId(review.getId())
                .memberId(review.getMemberId())
                .courseId(review.getCourseId())
                .rating(review.getRating())
                .content(review.getContent())
                .imageUrls(images.stream().map(ReviewImage::getImageUrl).toList())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}