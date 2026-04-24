package four_tential.potential.infra.redis.config;

import four_tential.potential.application.review.ReviewCacheService;
import four_tential.potential.domain.review.fixture.ReviewFixture;
import four_tential.potential.domain.review.fixture.ReviewImageFixture;
import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.domain.review.review_image.ReviewImage;
import four_tential.potential.domain.review.review_image.ReviewImageRepository;
import four_tential.potential.presentation.review.dto.response.ReviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewCacheServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewImageRepository reviewImageRepository;

    @InjectMocks
    private ReviewCacheService reviewCacheService;

    private static final UUID COURSE_ID = UUID.randomUUID();

    @Nested
    @DisplayName("getCachedReviews() - 후기 목록 페이지 조회")
    class GetCachedReviewsTest {

        @Test
        @DisplayName("후기 목록을 페이지 단위로 반환한다")
        void getCachedReviews_success() {
            // given
            Review review = ReviewFixture.defaultReview();
            Page<Review> reviewPage = new PageImpl<>(List.of(review));

            when(reviewRepository.findAllByCourseId(eq(COURSE_ID), any(Pageable.class))).thenReturn(reviewPage);
            when(reviewImageRepository.findAllByReviewIdIn(any())).thenReturn(List.of());

            // when
            List<ReviewResponse> result = reviewCacheService.getCachedReviews(COURSE_ID, 0, 20);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getRating()).isEqualTo(ReviewFixture.DEFAULT_RATING);
            assertThat(result.get(0).getContent()).isEqualTo(ReviewFixture.DEFAULT_CONTENT);
        }

        @Test
        @DisplayName("후기가 없으면 빈 리스트를 반환한다")
        void getCachedReviews_empty() {
            // given
            when(reviewRepository.findAllByCourseId(eq(COURSE_ID), any(Pageable.class))).thenReturn(Page.empty());

            // when
            List<ReviewResponse> result = reviewCacheService.getCachedReviews(COURSE_ID, 0, 20);

            // then
            assertThat(result).isEmpty();
            // 후기가 없으면 이미지 조회 쿼리가 발생하지 않아야 한다
            verify(reviewImageRepository, never()).findAllByReviewIdIn(any());
        }

        @Test
        @DisplayName("이미지를 리뷰 ID 목록으로 한 번에 일괄 조회한다 (N+1 방지)")
        void getCachedReviews_queriesImagesInBatch() {
            // given
            Review r1 = ReviewFixture.defaultReview();
            Review r2 = ReviewFixture.reviewWithRating(3);
            Page<Review> reviewPage = new PageImpl<>(List.of(r1, r2));

            when(reviewRepository.findAllByCourseId(eq(COURSE_ID), any(Pageable.class))).thenReturn(reviewPage);
            when(reviewImageRepository.findAllByReviewIdIn(any())).thenReturn(List.of());

            // when
            reviewCacheService.getCachedReviews(COURSE_ID, 0, 20);

            // then: 리뷰가 2개여도 이미지 쿼리는 1번만 나가야 한다
            verify(reviewImageRepository, times(1)).findAllByReviewIdIn(any());
        }

        @Test
        @DisplayName("이미지가 있는 후기는 imageUrls 를 포함해 반환한다")
        void getCachedReviews_withImages() throws Exception {
            // given
            Review review = ReviewFixture.defaultReview();
            // @UuidGenerator는 DB INSERT 시점에 id 생성 → 단위 테스트에서 리플렉션으로 주입
            UUID reviewId = UUID.randomUUID();
            java.lang.reflect.Field idField = Review.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(review, reviewId);

            Page<Review> reviewPage = new PageImpl<>(List.of(review));

            // 동일한 review 객체를 사용하는 이미지 생성
            ReviewImage image = ReviewImage.register(review, ReviewImageFixture.DEFAULT_IMAGE_URL);

            when(reviewRepository.findAllByCourseId(eq(COURSE_ID), any(Pageable.class))).thenReturn(reviewPage);
            when(reviewImageRepository.findAllByReviewIdIn(any())).thenReturn(List.of(image));

            // when
            List<ReviewResponse> result = reviewCacheService.getCachedReviews(COURSE_ID, 0, 20);

            // then
            assertThat(result).hasSize(1);
            assertThat(result.get(0).getImageUrls()).hasSize(1);
            assertThat(result.get(0).getImageUrls().get(0)).isEqualTo(ReviewImageFixture.DEFAULT_IMAGE_URL);
        }

        @Test
        @DisplayName("page, size 파라미터가 Pageable 로 올바르게 전달된다")
        void getCachedReviews_pageableApplied() {
            // given
            when(reviewRepository.findAllByCourseId(eq(COURSE_ID), any(Pageable.class))).thenReturn(Page.empty());

            // when
            reviewCacheService.getCachedReviews(COURSE_ID, 2, 10);

            // then
            verify(reviewRepository).findAllByCourseId(eq(COURSE_ID), argThat(pageable ->
                    pageable.getPageNumber() == 2 && pageable.getPageSize() == 10
            ));
        }

        @Test
        @DisplayName("created_at DESC 정렬이 적용된다")
        void getCachedReviews_sortByCreatedAtDesc() {
            // given
            when(reviewRepository.findAllByCourseId(eq(COURSE_ID), any(Pageable.class))).thenReturn(Page.empty());

            // when
            reviewCacheService.getCachedReviews(COURSE_ID, 0, 20);

            // then
            verify(reviewRepository).findAllByCourseId(eq(COURSE_ID), argThat(pageable ->
                    pageable.getSort().getOrderFor("createdAt") != null &&
                            pageable.getSort().getOrderFor("createdAt").isDescending()
            ));
        }
    }

    @Nested
    @DisplayName("evictAll() - 캐시 전체 무효화")
    class EvictAllTest {

        @Test
        @DisplayName("evictAll 호출 시 예외 없이 정상 종료된다")
        void evictAll_success() {
            // @CacheEvict는 AOP 프록시를 통해 동작하므로 단위 테스트에서는 메서드 자체 실행만 검증
            // 실제 캐시 삭제는 통합 테스트에서 검증
            reviewCacheService.evictAll();
            // 예외 없이 정상 종료되면 통과
        }
    }
}