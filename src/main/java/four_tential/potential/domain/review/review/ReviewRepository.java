package four_tential.potential.domain.review.review;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewRepositoryCustom {

    List<Review> findAllByCourseId(UUID courseId);

    // 캐싱용 페이지네이션 조회
    Page<Review> findAllByCourseId(UUID courseId, Pageable pageable);

    Optional<Review> findByIdAndMemberId(UUID id, UUID memberId);

    boolean existsByOrderIdAndMemberId(UUID orderId, UUID memberId);

    long countByCourseId(UUID courseId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.courseId = :courseId")
    Double findAverageRatingByCourseId(@Param("courseId") UUID courseId);
}