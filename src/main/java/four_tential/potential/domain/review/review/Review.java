package four_tential.potential.domain.review.review;

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
        name = "reviews",
        indexes = {
                // course_id 조건 + created_at 정렬을 Index Scan 한 번으로 처리
                @Index(name = "idx_reviews_course_created", columnList = "course_id, created_at DESC")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Review extends BaseTimeEntity {

    @Id
    @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
    @Column(nullable = false, updatable = false, columnDefinition = "BINARY(16)")
    private UUID id;

    @Column(name = "member_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID memberId;

    @Column(name = "course_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID courseId;

    @Column(name = "order_id", nullable = false, columnDefinition = "BINARY(16)")
    private UUID orderId;

    @Column(nullable = false)
    private int rating;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    public static Review register(UUID memberId, UUID courseId, UUID orderId, int rating, String content) {
        Review review = new Review();
        review.memberId = memberId;
        review.courseId = courseId;
        review.orderId = orderId;
        review.rating = rating;
        review.content = content;
        return review;
    }

    public void update(int rating, String content) {
        this.rating = rating;
        this.content = content;
    }
}