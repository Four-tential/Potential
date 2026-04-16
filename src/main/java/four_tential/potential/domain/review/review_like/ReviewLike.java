package four_tential.potential.domain.review.review_like;

import four_tential.potential.common.entity.BaseTimeEntity;
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

    @Column(name = "reviews_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID reviewId;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    public static ReviewLike register(UUID reviewId, UUID memberId) {
        ReviewLike like = new ReviewLike();
        like.reviewId = reviewId;
        like.memberId = memberId;
        return like;
    }
}