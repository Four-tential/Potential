package four_tential.potential.domain.review.review;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewRepositoryCustom {

    List<Review> findAllByCourseId(UUID courseId);

    Optional<Review> findByIdAndMemberId(UUID id, UUID memberId);

    boolean existsByOrderIdAndMemberId(UUID orderId, UUID memberId);

    long countByCourseId(UUID courseId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.courseId = :courseId")
    Double findAverageRatingByCourseId(@Param("courseId") UUID courseId);
}
