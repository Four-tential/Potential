package four_tential.potential.application.review;

import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.domain.review.review_image.ReviewImage;
import four_tential.potential.domain.review.review_image.ReviewImageRepository;
import four_tential.potential.presentation.review.dto.response.ReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import static four_tential.potential.infra.redis.RedisConstants.REVIEW_LIST_CACHE;

/**
 * 후기 목록 캐싱 전담 컴포넌트 (Cache-Aside 전략)
 *
 * [설계 이유]
 * 1. Self-invocation 문제 방지
 *    - Spring @Cacheable은 AOP 프록시 기반으로 동작
 *    - 같은 클래스(ReviewService) 내에서 직접 호출하면 프록시를 거치지 않아 캐시 미적용
 *    - 별도 빈으로 분리하여 프록시를 통해 호출되도록 설계
 *
 * 2. 직렬화 안정성
 *    - PageResponse<ReviewResponse> (제네릭 record) 대신 List<ReviewResponse>만 캐싱
 *    - 제네릭 타입은 Jackson 역직렬화 시 타입 소거로 실패할 수 있음
 *    - List<ReviewResponse>는 단순 타입으로 안정적으로 직렬화/역직렬화 가능
 */
@Service
@RequiredArgsConstructor
public class ReviewCacheService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;

    /**
     * 후기 목록 페이지 조회 (캐시 적용)
     * 캐시 키: reviewList::{courseId}:{page}:{size}
     * TTL: 10분
     */
    @Cacheable(
            cacheNames = REVIEW_LIST_CACHE,
            key = "#courseId + ':' + #page + ':' + #size"
    )
    @Transactional(readOnly = true)
    public List<ReviewResponse> getCachedReviews(UUID courseId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Review> reviewPage = reviewRepository.findAllByCourseId(courseId, pageable);

        if (reviewPage.isEmpty()) {
            return List.of();
        }

        // 이미지 일괄 조회 (N+1 방지)
        List<UUID> reviewIds = reviewPage.getContent().stream()
                .map(Review::getId)
                .toList();
        List<ReviewImage> allImages = reviewImageRepository.findAllByReviewIdIn(reviewIds);

        Map<UUID, List<ReviewImage>> imagesByReviewId = allImages.stream()
                .collect(Collectors.groupingBy(image -> image.getReview().getId()));

        return reviewPage.getContent().stream()
                .map(review -> ReviewResponse.of(
                        review,
                        imagesByReviewId.getOrDefault(review.getId(), List.of())
                ))
                .toList();
    }

    /**
     * 후기 쓰기(작성/수정/삭제) 시 전체 페이지 캐시 무효화
     * allEntries = true: 해당 캐시 이름의 모든 키 삭제
     */
    @CacheEvict(cacheNames = REVIEW_LIST_CACHE, allEntries = true)
    public void evictAll() {
        // AOP가 캐시 삭제 처리 - 메서드 본문 불필요
    }
}