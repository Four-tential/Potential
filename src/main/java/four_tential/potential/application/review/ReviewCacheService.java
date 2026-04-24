package four_tential.potential.application.review;

import four_tential.potential.common.dto.PageResponse;
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

@Service
@RequiredArgsConstructor
public class ReviewCacheService {

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;

     // 후기 목록 페이지 조회 (캐시 적용)
     // 캐시 키: reviewList::{courseId}:{page}:{size}
    @Cacheable(
            cacheNames = REVIEW_LIST_CACHE,
            key = "#courseId + ':' + #page + ':' + #size"
    )
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> getCachedReviews(UUID courseId, int page, int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        Page<Review> reviewPage = reviewRepository.findAllByCourseId(courseId, pageable);

        if (reviewPage.isEmpty()) {
            return PageResponse.register(reviewPage.map(r -> ReviewResponse.of(r, List.of())));
        }

        // 이미지 일괄 조회 (N+1 방지)
        List<UUID> reviewIds = reviewPage.getContent().stream()
                .map(Review::getId)
                .toList();
        List<ReviewImage> allImages = reviewImageRepository.findAllByReviewIdIn(reviewIds);

        Map<UUID, List<ReviewImage>> imagesByReviewId = allImages.stream()
                .collect(Collectors.groupingBy(image -> image.getReview().getId()));

        return PageResponse.register(
                reviewPage.map(review -> ReviewResponse.of(
                        review,
                        imagesByReviewId.getOrDefault(review.getId(), List.of())
                ))
        );
    }

     //후기 쓰기(작성/수정/삭제) 시 전체 페이지 캐시 무효화
    @CacheEvict(cacheNames = REVIEW_LIST_CACHE, allEntries = true)
    public void evictAll() {
        // AOP가 캐시 삭제 처리 - 메서드 본문 불필요
    }
}