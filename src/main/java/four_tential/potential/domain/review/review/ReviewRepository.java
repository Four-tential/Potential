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
    // 캐싱용 페이지네이션 조회
    Page<Review> findAllByCourseId(UUID courseId, Pageable pageable);

    Optional<Review> findByIdAndMemberId(UUID id, UUID memberId);

    boolean existsByOrderIdAndMemberId(UUID orderId, UUID memberId);

    long countByCourseId(UUID courseId);

    @Query("SELECT AVG(r.rating) FROM Review r WHERE r.courseId = :courseId")
    Double findAverageRatingByCourseId(@Param("courseId") UUID courseId);

    // 배치 재요약용 — 후기가 있는 코스 ID 목록 조회
    @Query("SELECT DISTINCT r.courseId FROM Review r")
    List<UUID> findDistinctCourseIds();

    // 배치 재요약용 — 코스의 전체 후기 content 조회
    @Query("SELECT r.content FROM Review r WHERE r.courseId = :courseId ORDER BY r.createdAt ASC")
    List<String> findAllContentByCourseId(@Param("courseId") UUID courseId);

    // 배치 재요약용 — 코스의 전체 후기 rating + content 조회
    @Query("SELECT new four_tential.potential.domain.review.review.ReviewSummaryItem(r.rating, r.content) FROM Review r WHERE r.courseId = :courseId ORDER BY r.createdAt ASC")
    List<ReviewSummaryItem> findAllSummaryItemsByCourseId(@Param("courseId") UUID courseId);
}