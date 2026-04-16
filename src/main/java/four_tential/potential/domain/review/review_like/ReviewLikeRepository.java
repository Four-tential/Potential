package four_tential.potential.domain.review.review_like;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, UUID> {

    Optional<ReviewLike> findByReviewIdAndMemberId(UUID reviewId, UUID memberId);

    boolean existsByReviewIdAndMemberId(UUID reviewId, UUID memberId);

    long countByReviewId(UUID reviewId);
}