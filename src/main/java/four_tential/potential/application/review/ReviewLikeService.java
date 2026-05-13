package four_tential.potential.application.review;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.domain.review.review_like.ReviewLike;
import four_tential.potential.domain.review.review_like.ReviewLikeRepository;
import four_tential.potential.presentation.review.dto.response.ReviewLikeResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.ReviewExceptionEnum.*;

/**
 * 후기 좋아요 토글 트랜잭션 전담 빈
 *
 * [설계 이유]
 * toggleLike()에서 Redisson 분산 락을 획득한 뒤 이 빈을 호출하면
 * Spring AOP 프록시를 통해 @Transactional이 정상 동작합니다.
 * (같은 클래스 내 self-invocation 시 @Transactional 무시 문제 방지)
 *
 * 락 해제는 호출자(ReviewService.toggleLike)의 TransactionSynchronization.afterCompletion()에서 처리합니다.
 */
@Service
@RequiredArgsConstructor
public class ReviewLikeService {

    private final ReviewRepository reviewRepository;
    private final ReviewLikeRepository reviewLikeRepository;

    @Transactional
    public ReviewLikeResponse toggle(UUID memberId, UUID reviewId) {
        // 후기 존재 여부 확인
        var review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ServiceErrorException(ERR_REVIEW_NOT_FOUND));

        // 자기 자신 후기 좋아요 방지
        if (review.getMemberId().equals(memberId)) {
            throw new ServiceErrorException(ERR_SELF_LIKE_FORBIDDEN);
        }

        // 이미 좋아요 → 해제 / 없으면 → 등록
        Optional<ReviewLike> existing = reviewLikeRepository.findByReviewIdAndMemberId(reviewId, memberId);
        if (existing.isPresent()) {
            reviewLikeRepository.delete(existing.get());
        } else {
            reviewLikeRepository.save(ReviewLike.register(reviewId, memberId));
        }

        // 좋아요 수와 본인 여부를 단일 쿼리로 조회 (DB 조회 2회 → 1회)
        Object[] result = reviewLikeRepository.findCountAndLikedStatus(reviewId, memberId);
        long likeCount = result[0] == null ? 0L : ((Number) result[0]).longValue();
        boolean liked  = result[1] != null && ((Number) result[1]).longValue() > 0;

        return ReviewLikeResponse.of(reviewId, likeCount, liked);
    }
}