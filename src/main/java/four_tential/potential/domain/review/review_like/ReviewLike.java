package four_tential.potential.domain.review.review_like;

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
@Table(
        name = "review_likes",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_review_likes_review_member", columnNames = {"reviews_id", "member_id"})
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ReviewLike extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviews_id", nullable = false)
    private Review review;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    public static ReviewLike register(Review review, UUID memberId) {
        ReviewLike like = new ReviewLike();
        like.review = review;
        like.memberId = memberId;
        return like;
    }
}