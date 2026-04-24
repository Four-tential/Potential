package four_tential.potential.presentation.review;

import four_tential.potential.application.review.ReviewService;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.infra.security.principal.MemberPrincipal;
import four_tential.potential.presentation.review.dto.request.ReviewCreateRequest;
import four_tential.potential.presentation.review.dto.request.ReviewUpdateRequest;
import four_tential.potential.presentation.review.dto.response.ReviewLikeResponse;
import four_tential.potential.presentation.review.dto.response.ReviewResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import four_tential.potential.common.dto.PageResponse;
import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.ReviewExceptionEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewControllerTest {

    @Mock private ReviewService reviewService;

    @InjectMocks
    private ReviewController reviewController;

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();

    private MemberPrincipal principal;

    @BeforeEach
    void setUp() {
        principal = new MemberPrincipal(MEMBER_ID, "test@test.com", "ROLE_STUDENT");
    }

    private ReviewResponse stubResponse() {
        return new ReviewResponse(REVIEW_ID, MEMBER_ID, COURSE_ID, 5, "좋아요", List.of(), null, null);
    }

    private ReviewCreateRequest createRequest(int rating, String content, List<String> imageUrls) {
        ReviewCreateRequest req = mock(ReviewCreateRequest.class);
        when(req.getOrderId()).thenReturn(ORDER_ID);
        when(req.getRating()).thenReturn(rating);
        when(req.getContent()).thenReturn(content);
        when(req.getImageUrls()).thenReturn(imageUrls);
        return req;
    }

    private ReviewUpdateRequest updateRequest(int rating, String content, List<String> imageUrls) {
        ReviewUpdateRequest req = mock(ReviewUpdateRequest.class);
        when(req.getRating()).thenReturn(rating);
        when(req.getContent()).thenReturn(content);
        when(req.getImageUrls()).thenReturn(imageUrls);
        return req;
    }

    @Nested
    @DisplayName("create() - 후기 작성")
    class CreateTest {

        @Test
        @DisplayName("후기 작성 성공 시 201 과 ReviewResponse 를 반환한다")
        void create_success() {
            ReviewCreateRequest request = createRequest(5, "좋아요", List.of());
            when(reviewService.create(eq(MEMBER_ID), eq(COURSE_ID), eq(ORDER_ID), eq(5), eq("좋아요"), any()))
                    .thenReturn(stubResponse());

            ResponseEntity<?> response = reviewController.create(COURSE_ID, request, principal);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
            assertThat(response.getBody()).isNotNull();
            verify(reviewService).create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", List.of());
        }

        @Test
        @DisplayName("이미지 URL 포함 요청 시 서비스에 imageUrls 가 전달된다")
        void create_withImageUrls_passesToService() {
            List<String> urls = List.of("https://cdn.test/a.jpg");
            ReviewCreateRequest request = createRequest(5, "좋아요", urls);
            when(reviewService.create(any(), any(), any(), anyInt(), any(), eq(urls)))
                    .thenReturn(stubResponse());

            reviewController.create(COURSE_ID, request, principal);

            verify(reviewService).create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", urls);
        }

        @Test
        @DisplayName("서비스에서 예외 발생 시 예외가 전파된다")
        void create_serviceThrows_propagatesException() {
            ReviewCreateRequest request = createRequest(5, "내용", List.of());
            when(reviewService.create(any(), any(), any(), anyInt(), any(), any()))
                    .thenThrow(new ServiceErrorException(ERR_ORDER_NOT_CONFIRMED));

            assertThatThrownBy(() -> reviewController.create(COURSE_ID, request, principal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_ORDER_NOT_CONFIRMED.getMessage());
        }

        @Test
        @DisplayName("이미 후기를 작성한 경우 예외가 전파된다")
        void create_alreadyReviewed_propagatesException() {
            ReviewCreateRequest request = createRequest(5, "내용", List.of());
            when(reviewService.create(any(), any(), any(), anyInt(), any(), any()))
                    .thenThrow(new ServiceErrorException(ERR_ALREADY_REVIEWED));

            assertThatThrownBy(() -> reviewController.create(COURSE_ID, request, principal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_ALREADY_REVIEWED.getMessage());
        }

        @Test
        @DisplayName("출석하지 않은 경우 예외가 전파된다")
        void create_notAttended_propagatesException() {
            ReviewCreateRequest request = createRequest(5, "내용", List.of());
            when(reviewService.create(any(), any(), any(), anyInt(), any(), any()))
                    .thenThrow(new ServiceErrorException(ERR_NOT_ATTENDED));

            assertThatThrownBy(() -> reviewController.create(COURSE_ID, request, principal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_NOT_ATTENDED.getMessage());
        }
    }

    @Nested
    @DisplayName("findAllByCourse() - 코스별 후기 목록 페이지 조회")
    class FindAllByCourseTest {

        @Test
        @DisplayName("후기 목록 조회 성공 시 200 과 PageResponse 를 반환한다")
        void findAllByCourse_success() {
            PageResponse<ReviewResponse> pageResponse = new PageResponse<>(
                    List.of(stubResponse()), 0, 1, 1L, 20, true
            );
            when(reviewService.findAllByCourse(COURSE_ID, 0, 20)).thenReturn(pageResponse);

            ResponseEntity<?> response = reviewController.findAllByCourse(COURSE_ID, 0, 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(reviewService).findAllByCourse(COURSE_ID, 0, 20);
        }

        @Test
        @DisplayName("후기가 없으면 빈 content 와 200 을 반환한다")
        void findAllByCourse_empty() {
            PageResponse<ReviewResponse> emptyPage = new PageResponse<>(
                    List.of(), 0, 0, 0L, 20, true
            );
            when(reviewService.findAllByCourse(COURSE_ID, 0, 20)).thenReturn(emptyPage);

            ResponseEntity<?> response = reviewController.findAllByCourse(COURSE_ID, 0, 20);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }
    }

    @Nested
    @DisplayName("findById() - 후기 단건 조회")
    class FindByIdTest {

        @Test
        @DisplayName("후기 단건 조회 성공 시 200 과 ReviewResponse 를 반환한다")
        void findById_success() {
            when(reviewService.findById(REVIEW_ID)).thenReturn(stubResponse());

            ResponseEntity<?> response = reviewController.findById(REVIEW_ID);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(reviewService).findById(REVIEW_ID);
        }

        @Test
        @DisplayName("후기가 없으면 ERR_REVIEW_NOT_FOUND 예외가 전파된다")
        void findById_notFound_propagatesException() {
            when(reviewService.findById(REVIEW_ID))
                    .thenThrow(new ServiceErrorException(ERR_REVIEW_NOT_FOUND));

            assertThatThrownBy(() -> reviewController.findById(REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_NOT_FOUND.getMessage());
        }
    }

    @Nested
    @DisplayName("update() - 후기 수정")
    class UpdateTest {

        @Test
        @DisplayName("후기 수정 성공 시 200 과 수정된 ReviewResponse 를 반환한다")
        void update_success() {
            ReviewUpdateRequest request = updateRequest(3, "수정된 내용", List.of());
            ReviewResponse updated = new ReviewResponse(REVIEW_ID, MEMBER_ID, COURSE_ID, 3, "수정된 내용", List.of(), null, null);

            when(reviewService.update(eq(MEMBER_ID), eq(REVIEW_ID), eq(3), eq("수정된 내용"), any()))
                    .thenReturn(updated);

            ResponseEntity<?> response = reviewController.update(REVIEW_ID, request, principal);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(reviewService).update(MEMBER_ID, REVIEW_ID, 3, "수정된 내용", List.of());
        }

        @Test
        @DisplayName("본인 후기가 아니면 ERR_REVIEW_FORBIDDEN 예외가 전파된다")
        void update_notOwner_propagatesException() {
            ReviewUpdateRequest request = updateRequest(3, "수정", List.of());
            when(reviewService.update(any(), any(), anyInt(), any(), any()))
                    .thenThrow(new ServiceErrorException(ERR_REVIEW_FORBIDDEN));

            assertThatThrownBy(() -> reviewController.update(REVIEW_ID, request, principal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("수정 기간이 지나면 ERR_REVIEW_PERIOD_EXPIRED 예외가 전파된다")
        void update_periodExpired_propagatesException() {
            ReviewUpdateRequest request = updateRequest(3, "수정", List.of());
            when(reviewService.update(any(), any(), anyInt(), any(), any()))
                    .thenThrow(new ServiceErrorException(ERR_REVIEW_PERIOD_EXPIRED));

            assertThatThrownBy(() -> reviewController.update(REVIEW_ID, request, principal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_PERIOD_EXPIRED.getMessage());
        }
    }

    @Nested
    @DisplayName("delete() - 후기 삭제")
    class DeleteTest {

        @Test
        @DisplayName("후기 삭제 성공 시 200 을 반환한다")
        void delete_success() {
            doNothing().when(reviewService).delete(MEMBER_ID, REVIEW_ID);

            ResponseEntity<?> response = reviewController.delete(REVIEW_ID, principal);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            verify(reviewService).delete(MEMBER_ID, REVIEW_ID);
        }

        @Test
        @DisplayName("본인 후기가 아니면 ERR_REVIEW_FORBIDDEN 예외가 전파된다")
        void delete_notOwner_propagatesException() {
            doThrow(new ServiceErrorException(ERR_REVIEW_FORBIDDEN))
                    .when(reviewService).delete(MEMBER_ID, REVIEW_ID);

            assertThatThrownBy(() -> reviewController.delete(REVIEW_ID, principal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("삭제 성공 시 서비스가 정확히 1번 호출된다")
        void delete_callsServiceOnce() {
            doNothing().when(reviewService).delete(MEMBER_ID, REVIEW_ID);

            reviewController.delete(REVIEW_ID, principal);

            verify(reviewService, times(1)).delete(MEMBER_ID, REVIEW_ID);
        }
    }

    // ── toggleLike() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleLike() - 후기 좋아요 토글")
    class ToggleLikeTest {

        private ReviewLikeResponse stubLikeResponse(boolean liked, long count) {
            return ReviewLikeResponse.of(REVIEW_ID, count, liked);
        }

        @Test
        @DisplayName("좋아요 토글 성공 시 200 과 ReviewLikeResponse 를 반환한다")
        void toggleLike_success() {
            // given
            when(reviewService.toggleLike(MEMBER_ID, REVIEW_ID))
                    .thenReturn(stubLikeResponse(true, 1L));

            // when
            ResponseEntity<?> response = reviewController.toggleLike(REVIEW_ID, principal);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
            assertThat(response.getBody()).isNotNull();
            verify(reviewService).toggleLike(MEMBER_ID, REVIEW_ID);
        }

        @Test
        @DisplayName("좋아요 해제 시 liked=false 와 감소된 likeCount 를 반환한다")
        void toggleLike_cancel_returnsLikedFalse() {
            // given
            when(reviewService.toggleLike(MEMBER_ID, REVIEW_ID))
                    .thenReturn(stubLikeResponse(false, 0L));

            // when
            ResponseEntity<?> response = reviewController.toggleLike(REVIEW_ID, principal);

            // then
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("후기가 없으면 ERR_REVIEW_NOT_FOUND 예외가 전파된다")
        void toggleLike_reviewNotFound_propagatesException() {
            // given
            when(reviewService.toggleLike(MEMBER_ID, REVIEW_ID))
                    .thenThrow(new ServiceErrorException(ERR_REVIEW_NOT_FOUND));

            // when & then
            assertThatThrownBy(() -> reviewController.toggleLike(REVIEW_ID, principal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("자기 자신 후기 좋아요 시 ERR_SELF_LIKE_FORBIDDEN 예외가 전파된다")
        void toggleLike_selfLike_propagatesException() {
            // given
            when(reviewService.toggleLike(MEMBER_ID, REVIEW_ID))
                    .thenThrow(new ServiceErrorException(ERR_SELF_LIKE_FORBIDDEN));

            // when & then
            assertThatThrownBy(() -> reviewController.toggleLike(REVIEW_ID, principal))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_SELF_LIKE_FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("toggleLike 호출 시 서비스가 정확히 1번 호출된다")
        void toggleLike_callsServiceOnce() {
            // given
            when(reviewService.toggleLike(MEMBER_ID, REVIEW_ID))
                    .thenReturn(stubLikeResponse(true, 1L));

            // when
            reviewController.toggleLike(REVIEW_ID, principal);

            // then
            verify(reviewService, times(1)).toggleLike(MEMBER_ID, REVIEW_ID);
        }
    }
}