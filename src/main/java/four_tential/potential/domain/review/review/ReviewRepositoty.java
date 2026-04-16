package four_tential.potential.domain.review.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ReviewRepositoty extends JpaRepository<Review, UUID> {
}
