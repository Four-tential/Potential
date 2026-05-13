package four_tential.potential.domain.review.review_like;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface ReviewLikeRepository extends JpaRepository<ReviewLike, UUID> {

    Optional<ReviewLike> findByReviewIdAndMemberId(UUID reviewId, UUID memberId);

    boolean existsByReviewIdAndMemberId(UUID reviewId, UUID memberId);

    long countByReviewId(UUID reviewId);


    //  좋아요 수와 본인 좋아요 여부를 단일 쿼리로 조회
    //  toggleLike() 응답 생성 시 DB 조회 2회 -> 1회로 절감
    @Query("""
            SELECT COUNT(rl),
                   SUM(CASE WHEN rl.memberId = :memberId THEN 1 ELSE 0 END)
            FROM ReviewLike rl
            WHERE rl.reviewId = :reviewId
            """)
    Object[] findCountAndLikedStatus(@Param("reviewId") UUID reviewId,
                                     @Param("memberId") UUID memberId);
}