package four_tential.potential.domain.review.review_image;

import four_tential.potential.common.entity.BaseTimeEntity;
import four_tential.potential.domain.review.review.Review;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.UuidGenerator;

import java.util.UUID;

@Getter
@Entity
@Table(name = "review_images")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewImage extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviews_id", nullable = false)
    private Review review;

    @Column(name = "image_url", nullable = false, length = 300)
    private String imageUrl;

    public static ReviewImage register(Review review, String imageUrl) {
        ReviewImage image = new ReviewImage();
        image.review = review;
        image.imageUrl = imageUrl;
        return image;
    }
}