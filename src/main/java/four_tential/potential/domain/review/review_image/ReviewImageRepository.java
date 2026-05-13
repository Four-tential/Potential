package four_tential.potential.domain.review.review_image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, UUID> {

    List<ReviewImage> findAllByReviewId(UUID reviewId);

    List<ReviewImage> findAllByReviewIdIn(Collection<UUID> reviewIds);

    void deleteAllByReviewId(UUID reviewId);
}