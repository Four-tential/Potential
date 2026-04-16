package four_tential.potential.domain.review.review;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID> {

    List<Review> findAllByCourseId(UUID courseId);

    Optional<Review> findByIdAndMemberId(UUID id, UUID memberId);

    boolean existsByOrderIdAndMemberId(UUID orderId, UUID memberId);
}