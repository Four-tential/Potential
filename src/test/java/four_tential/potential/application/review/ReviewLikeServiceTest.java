package four_tential.potential.application.review;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.review.fixture.ReviewFixture;
import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.domain.review.review_like.ReviewLike;
import four_tential.potential.domain.review.review_like.ReviewLikeRepository;
import four_tential.potential.presentation.review.dto.response.ReviewLikeResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.ReviewExceptionEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewLikeServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewLikeRepository reviewLikeRepository;

    @InjectMocks
    private ReviewLikeService reviewLikeService;

    private static final UUID REVIEW_ID      = UUID.randomUUID();
    private static final UUID MEMBER_ID      = ReviewFixture.DEFAULT_MEMBER_ID;
    private static final UUID OTHER_MEMBER_ID = UUID.randomUUID();

    @Nested
    @DisplayName("toggle() - 후기 좋아요 토글")
    class ToggleTest {

        @Test
        @DisplayName("좋아요가 없으면 등록하고 liked=true 를 반환한다")
        void toggle_register_success() {
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{1L, 1L});

            ReviewLikeResponse result = reviewLikeService.toggle(OTHER_MEMBER_ID, REVIEW_ID);

            assertThat(result.getLikeCount()).isEqualTo(1L);
            assertThat(result.isLiked()).isTrue();
            verify(reviewLikeRepository).save(any(ReviewLike.class));
        }

        @Test
        @DisplayName("이미 좋아요가 있으면 해제하고 liked=false 를 반환한다")
        void toggle_cancel_success() {
            Review review = ReviewFixture.defaultReview();
            ReviewLike existing = ReviewLike.register(REVIEW_ID, OTHER_MEMBER_ID);

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.of(existing));
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{0L, 0L});

            ReviewLikeResponse result = reviewLikeService.toggle(OTHER_MEMBER_ID, REVIEW_ID);

            assertThat(result.getLikeCount()).isZero();
            assertThat(result.isLiked()).isFalse();
            verify(reviewLikeRepository).delete(existing);
        }

        @Test
        @DisplayName("후기가 없으면 ERR_REVIEW_NOT_FOUND 를 던진다")
        void toggle_reviewNotFound_throwsException() {
            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewLikeService.toggle(OTHER_MEMBER_ID, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_NOT_FOUND.getMessage());

            verify(reviewLikeRepository, never()).save(any());
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("자기 자신의 후기에 좋아요 시 ERR_SELF_LIKE_FORBIDDEN 를 던진다")
        void toggle_selfLike_throwsException() {
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

            assertThatThrownBy(() -> reviewLikeService.toggle(MEMBER_ID, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_SELF_LIKE_FORBIDDEN.getMessage());

            verify(reviewLikeRepository, never()).save(any());
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("좋아요 등록 시 save 가 1번만 호출된다")
        void toggle_register_savesOnce() {
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{1L, 1L});

            reviewLikeService.toggle(OTHER_MEMBER_ID, REVIEW_ID);

            verify(reviewLikeRepository, times(1)).save(any(ReviewLike.class));
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("좋아요 해제 시 delete 가 1번만 호출된다")
        void toggle_cancel_deletesOnce() {
            Review review = ReviewFixture.defaultReview();
            ReviewLike existing = ReviewLike.register(REVIEW_ID, OTHER_MEMBER_ID);

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.of(existing));
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{0L, 0L});

            reviewLikeService.toggle(OTHER_MEMBER_ID, REVIEW_ID);

            verify(reviewLikeRepository, times(1)).delete(existing);
            verify(reviewLikeRepository, never()).save(any());
        }

        @Test
        @DisplayName("반환된 ReviewLikeResponse 에 reviewId 가 포함된다")
        void toggle_responseContainsReviewId() {
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{1L, 1L});

            ReviewLikeResponse result = reviewLikeService.toggle(OTHER_MEMBER_ID, REVIEW_ID);

            assertThat(result.getReviewId()).isEqualTo(REVIEW_ID);
        }
    }
}