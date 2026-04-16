package four_tential.potential.domain.review.review_image;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewImageRepository extends JpaRepository<ReviewImage, UUID> {
}
