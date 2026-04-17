package four_tential.potential.application.review;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.domain.review.fixture.ReviewFixture;
import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.domain.review.review_image.ReviewImage;
import four_tential.potential.domain.review.review_image.ReviewImageRepository;
import four_tential.potential.domain.review.review_like.ReviewLike;
import four_tential.potential.domain.review.review_like.ReviewLikeRepository;
import four_tential.potential.presentation.review.dto.response.ReviewLikeResponse;
import four_tential.potential.presentation.review.dto.response.ReviewResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import static four_tential.potential.common.exception.domain.ReviewExceptionEnum.*;
import static four_tential.potential.common.exception.domain.OrderExceptionEnum.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ReviewServiceTest {

    @Mock private ReviewRepository reviewRepository;
    @Mock private ReviewImageRepository reviewImageRepository;
    @Mock private ReviewLikeRepository reviewLikeRepository;
    @Mock private CourseRepository courseRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private AttendanceRepository attendanceRepository;

    @InjectMocks
    private ReviewService reviewService;

    private static final UUID MEMBER_ID = UUID.randomUUID();
    private static final UUID COURSE_ID = UUID.randomUUID();
    private static final UUID ORDER_ID = UUID.randomUUID();
    private static final UUID REVIEW_ID = UUID.randomUUID();


    // CONFIRMED 상태 Order — 리플렉션으로 status 직접 주입
    private Order confirmedOrder() {
        Order order = Order.register(MEMBER_ID, COURSE_ID, 1, BigInteger.valueOf(50000), "테스트 클래스");
        try {
            java.lang.reflect.Field f = Order.class.getDeclaredField("status");
            f.setAccessible(true);
            f.set(order, OrderStatus.CONFIRMED);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return order;
    }

    // CLOSED 상태 Course, end_at = 현재 기준 N일 전 — 리플렉션으로 status/endAt 직접 주입
    private Course closedCourse(int daysAgo) {
        LocalDateTime end = LocalDateTime.now().minusDays(daysAgo);
        LocalDateTime start = end.minusHours(2);
        LocalDateTime orderClose = start.minusHours(3);
        LocalDateTime orderOpen  = orderClose.minusDays(5);

        Course course = Course.register(
                UUID.randomUUID(), UUID.randomUUID(),
                "테스트 클래스", "설명",
                "서울시 강남구", "2층",
                BigInteger.valueOf(50000),
                four_tential.potential.domain.course.course.CourseLevel.BEGINNER,
                orderOpen, orderClose, start, end
        );
        try {
            java.lang.reflect.Field statusField = Course.class.getDeclaredField("status");
            statusField.setAccessible(true);
            statusField.set(course, four_tential.potential.domain.course.course.CourseStatus.CLOSED);

            java.lang.reflect.Field endAtField = Course.class.getDeclaredField("endAt");
            endAtField.setAccessible(true);
            endAtField.set(course, end);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        return course;
    }

    // ATTEND 상태 Attendance
    private Attendance attendedAttendance() {
        Attendance a = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID);
        a.attend("qr-token");
        return a;
    }


    @Nested
    @DisplayName("create() - 후기 작성")
    class CreateTest {

        @Test
        @DisplayName("정상 조건을 모두 충족하면 후기를 저장하고 ReviewResponse 를 반환한다")
        void create_success() {
            // given
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance attendance = attendedAttendance();

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            // when
            ReviewResponse response = reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", List.of());

            // then
            assertThat(response.getRating()).isEqualTo(5);
            assertThat(response.getContent()).isEqualTo("좋아요");
            verify(reviewRepository).save(any(Review.class));
        }

        @Test
        @DisplayName("이미지 URL 이 있으면 ReviewImage 를 함께 저장한다")
        void create_withImages_savesImages() {
            // given
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance attendance = attendedAttendance();
            List<String> imageUrls = List.of("https://cdn.test/a.jpg", "https://cdn.test/b.jpg");

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reviewImageRepository.saveAll(any())).thenAnswer(inv -> inv.getArgument(0));

            // when
            reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", imageUrls);

            // then
            verify(reviewImageRepository).saveAll(argThat(list ->
                    ((List<?>) list).size() == 2
            ));
        }

        @Test
        @DisplayName("주문이 존재하지 않으면 ERR_NOT_FOUND_ORDER 를 던진다")
        void create_orderNotFound_throwsException() {
            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_NOT_FOUND_ORDER.getMessage());
        }

        @Test
        @DisplayName("주문이 CONFIRMED 가 아니면 ERR_ORDER_NOT_CONFIRMED 를 던진다")
        void create_orderNotConfirmed_throwsException() {
            Order order = Order.register(MEMBER_ID, COURSE_ID, 1, BigInteger.valueOf(50000), "클래스");
            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_ORDER_NOT_CONFIRMED.getMessage());
        }

        @Test
        @DisplayName("주문의 courseId 가 파라미터 courseId 와 다르면 ERR_NOT_FOUND_ORDER 를 던진다")
        void create_courseIdMismatch_throwsException() {
            UUID anotherCourseId = UUID.randomUUID();
            Order order = confirmedOrder(); // COURSE_ID 로 생성된 주문

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));

            // 다른 courseId 로 요청
            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, anotherCourseId, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_NOT_FOUND_ORDER.getMessage());
        }

        @Test
        @DisplayName("타인의 주문 ID 를 넣으면 ERR_NOT_FOUND_ORDER 를 던진다")
        void create_otherMemberOrder_throwsException() {
            // findOrderDetailsById 는 memberId 까지 조건에 포함하므로 타인 주문은 조회 자체가 안 됨
            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_NOT_FOUND_ORDER.getMessage());
        }

        @Test
        @DisplayName("코스가 존재하지 않으면 ERR_REVIEW_NOT_FOUND 를 던진다")
        void create_courseNotFound_throwsException() {
            Order order = confirmedOrder();
            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("코스가 CLOSED 가 아니면 ERR_COURSE_NOT_CLOSED 를 던진다")
        void create_courseNotClosed_throwsException() {
            Order order = confirmedOrder();
            // OPEN 상태 코스 — 리플렉션으로 status 주입
            Course course = closedCourse(2);
            try {
                java.lang.reflect.Field f = Course.class.getDeclaredField("status");
                f.setAccessible(true);
                f.set(course, four_tential.potential.domain.course.course.CourseStatus.OPEN);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_COURSE_NOT_CLOSED.getMessage());
        }

        @Test
        @DisplayName("코스 종료 후 7일이 지났으면 ERR_REVIEW_PERIOD_EXPIRED 를 던진다")
        void create_reviewPeriodExpired_throwsException() {
            Order order = confirmedOrder();
            Course course = closedCourse(8); // 8일 전 종료

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_PERIOD_EXPIRED.getMessage());
        }

        @Test
        @DisplayName("출석 레코드가 없으면 ERR_NOT_ATTENDED 를 던진다")
        void create_attendanceNotFound_throwsException() {
            Order order = confirmedOrder();
            Course course = closedCourse(2);

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_NOT_ATTENDED.getMessage());
        }

        @Test
        @DisplayName("출석 상태가 ABSENT 면 ERR_NOT_ATTENDED 를 던진다")
        void create_attendanceAbsent_throwsException() {
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance absent = Attendance.register(ORDER_ID, MEMBER_ID, COURSE_ID); // ABSENT

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(absent));

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_NOT_ATTENDED.getMessage());
        }

        @Test
        @DisplayName("이미 후기를 작성했으면 ERR_ALREADY_REVIEWED 를 던진다")
        void create_alreadyReviewed_throwsException() {
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance attendance = attendedAttendance();

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(true);

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_ALREADY_REVIEWED.getMessage());
        }

        @Test
        @DisplayName("imageUrls 가 null 이면 이미지를 저장하지 않는다")
        void create_nullImageUrls_doesNotSaveImages() {
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance attendance = attendedAttendance();

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseId(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", null);

            verify(reviewImageRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("findAllByCourse() - 코스별 후기 목록 조회")
    class FindAllByCourseTest {

        @Test
        @DisplayName("해당 코스의 후기 목록을 반환한다")
        void findAllByCourse_success() {
            Review review = ReviewFixture.defaultReview();
            when(reviewRepository.findAllByCourseId(COURSE_ID)).thenReturn(List.of(review));
            when(reviewImageRepository.findAllByReviewIdIn(any())).thenReturn(List.of());

            List<ReviewResponse> result = reviewService.findAllByCourse(COURSE_ID);

            assertThat(result).hasSize(1);
            verify(reviewRepository).findAllByCourseId(COURSE_ID);
        }

        @Test
        @DisplayName("후기가 없으면 빈 리스트를 반환한다")
        void findAllByCourse_empty() {
            when(reviewRepository.findAllByCourseId(COURSE_ID)).thenReturn(List.of());

            List<ReviewResponse> result = reviewService.findAllByCourse(COURSE_ID);

            assertThat(result).isEmpty();
            verify(reviewImageRepository, never()).findAllByReviewIdIn(any());
        }

        @Test
        @DisplayName("이미지를 리뷰 ID 목록으로 한 번에 일괄 조회한다")
        void findAllByCourse_queriesImagesInBatch() {
            Review r1 = ReviewFixture.defaultReview();
            Review r2 = ReviewFixture.reviewWithRating(3);
            when(reviewRepository.findAllByCourseId(COURSE_ID)).thenReturn(List.of(r1, r2));
            when(reviewImageRepository.findAllByReviewIdIn(any())).thenReturn(List.of());

            reviewService.findAllByCourse(COURSE_ID);

            // N+1 해결 검증: 리뷰가 2개여도 이미지 쿼리는 1번만 나가야 한다
            verify(reviewImageRepository, times(1)).findAllByReviewIdIn(any());
        }
    }

    @Nested
    @DisplayName("findById() - 후기 단건 조회")
    class FindByIdTest {

        @Test
        @DisplayName("후기 단건을 정상 조회한다")
        void findById_success() {
            Review review = ReviewFixture.defaultReview();
            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewImageRepository.findAllByReviewId(any())).thenReturn(List.of());

            ReviewResponse result = reviewService.findById(REVIEW_ID);

            assertThat(result.getRating()).isEqualTo(ReviewFixture.DEFAULT_RATING);
            assertThat(result.getContent()).isEqualTo(ReviewFixture.DEFAULT_CONTENT);
        }

        @Test
        @DisplayName("후기가 없으면 ERR_REVIEW_NOT_FOUND 를 던진다")
        void findById_notFound_throwsException() {
            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.findById(REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_NOT_FOUND.getMessage());
        }

        @Test
        @DisplayName("이미지가 있는 후기 조회 시 imageUrls 를 함께 반환한다")
        void findById_withImages_returnsImageUrls() {
            Review review = ReviewFixture.defaultReview();
            ReviewImage image = ReviewImage.register(review, "https://cdn.test/img.jpg");
            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewImageRepository.findAllByReviewId(any())).thenReturn(List.of(image));

            ReviewResponse result = reviewService.findById(REVIEW_ID);

            assertThat(result.getImageUrls()).hasSize(1);
            assertThat(result.getImageUrls().get(0)).isEqualTo("https://cdn.test/img.jpg");
        }
    }

    @Nested
    @DisplayName("update() - 후기 수정")
    class UpdateTest {

        @Test
        @DisplayName("정상 조건이면 rating 과 content 를 수정하고 반환한다")
        void update_success() {
            Review review = ReviewFixture.defaultReview();
            Course course = closedCourse(2);

            when(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID))
                    .thenReturn(Optional.of(review));
            when(courseRepository.findById(review.getCourseId()))
                    .thenReturn(Optional.of(course));

            ReviewResponse result = reviewService.update(MEMBER_ID, REVIEW_ID, 3, "수정된 내용", List.of());

            assertThat(result.getRating()).isEqualTo(3);
            assertThat(result.getContent()).isEqualTo("수정된 내용");
        }

        @Test
        @DisplayName("본인 후기가 아니면 ERR_REVIEW_FORBIDDEN 를 던진다")
        void update_notOwner_throwsException() {
            when(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.update(MEMBER_ID, REVIEW_ID, 3, "수정", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_FORBIDDEN.getMessage());
        }

        @Test
        @DisplayName("수정 기간이 지났으면 ERR_REVIEW_PERIOD_EXPIRED 를 던진다")
        void update_periodExpired_throwsException() {
            Review review = ReviewFixture.defaultReview();
            Course course = closedCourse(8); // 8일 전 종료

            when(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID))
                    .thenReturn(Optional.of(review));
            when(courseRepository.findById(review.getCourseId()))
                    .thenReturn(Optional.of(course));

            assertThatThrownBy(() -> reviewService.update(MEMBER_ID, REVIEW_ID, 3, "수정", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_PERIOD_EXPIRED.getMessage());
        }

        @Test
        @DisplayName("수정 시 기존 이미지를 삭제하고 새 이미지를 저장한다")
        void update_replacesImages() {
            Review review = ReviewFixture.defaultReview();
            Course course = closedCourse(2);
            List<String> newUrls = List.of("https://cdn.test/new.jpg");

            when(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID))
                    .thenReturn(Optional.of(review));
            when(courseRepository.findById(review.getCourseId()))
                    .thenReturn(Optional.of(course));
            when(reviewImageRepository.saveAll(any())).thenReturn(List.of());

            reviewService.update(MEMBER_ID, REVIEW_ID, 4, "수정", newUrls);

            verify(reviewImageRepository).deleteAllByReviewId(REVIEW_ID);
            verify(reviewImageRepository).saveAll(argThat(list -> ((List<?>) list).size() == 1));
        }
    }

    @Nested
    @DisplayName("delete() - 후기 삭제")
    class DeleteTest {

        @Test
        @DisplayName("본인 후기를 정상 삭제한다")
        void delete_success() {
            Review review = ReviewFixture.defaultReview();
            when(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID))
                    .thenReturn(Optional.of(review));

            reviewService.delete(MEMBER_ID, REVIEW_ID);

            verify(reviewImageRepository).deleteAllByReviewId(REVIEW_ID);
            verify(reviewRepository).delete(review);
        }

        @Test
        @DisplayName("본인 후기가 아니면 ERR_REVIEW_FORBIDDEN 를 던진다")
        void delete_notOwner_throwsException() {
            when(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID))
                    .thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.delete(MEMBER_ID, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_FORBIDDEN.getMessage());

            verify(reviewImageRepository, never()).deleteAllByReviewId(any());
            verify(reviewRepository, never()).delete(any());
        }

        @Test
        @DisplayName("삭제 시 이미지를 먼저 삭제한 뒤 후기를 삭제한다")
        void delete_deletesImagesBeforeReview() {
            Review review = ReviewFixture.defaultReview();
            when(reviewRepository.findByIdAndMemberId(REVIEW_ID, MEMBER_ID))
                    .thenReturn(Optional.of(review));

            reviewService.delete(MEMBER_ID, REVIEW_ID);

            // 순서 검증
            var inOrder = inOrder(reviewImageRepository, reviewRepository);
            inOrder.verify(reviewImageRepository).deleteAllByReviewId(REVIEW_ID);
            inOrder.verify(reviewRepository).delete(review);
        }
    }

    // ── toggleLike() ─────────────────────────────────────────────────────

    @Nested
    @DisplayName("toggleLike() - 후기 좋아요 토글")
    class ToggleLikeTest {

        private static final UUID OTHER_MEMBER_ID = UUID.randomUUID();

        @Test
        @DisplayName("좋아요가 없으면 등록하고 liked=true 를 반환한다")
        void toggleLike_register_success() {
            // given
            Review review = ReviewFixture.defaultReview(); // memberId = ReviewFixture.DEFAULT_MEMBER_ID

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.countByReviewId(REVIEW_ID)).thenReturn(1L);
            when(reviewLikeRepository.existsByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(true);

            // when
            ReviewLikeResponse result = reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            // then
            assertThat(result.getLikeCount()).isEqualTo(1L);
            assertThat(result.isLiked()).isTrue();
            verify(reviewLikeRepository).saveAndFlush(any(ReviewLike.class));
        }

        @Test
        @DisplayName("이미 좋아요가 있으면 해제하고 liked=false 를 반환한다")
        void toggleLike_cancel_success() {
            // given
            Review review = ReviewFixture.defaultReview();
            ReviewLike existing = ReviewLike.register(REVIEW_ID, OTHER_MEMBER_ID);

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.of(existing));
            when(reviewLikeRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);
            when(reviewLikeRepository.existsByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(false);

            // when
            ReviewLikeResponse result = reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            // then
            assertThat(result.getLikeCount()).isEqualTo(0L);
            assertThat(result.isLiked()).isFalse();
            verify(reviewLikeRepository).delete(existing);
        }

        @Test
        @DisplayName("후기가 없으면 ERR_REVIEW_NOT_FOUND 를 던진다")
        void toggleLike_reviewNotFound_throwsException() {
            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_NOT_FOUND.getMessage());

            verify(reviewLikeRepository, never()).saveAndFlush(any());
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("자기 자신의 후기에 좋아요 시 ERR_SELF_LIKE_FORBIDDEN 를 던진다")
        void toggleLike_selfLike_throwsException() {
            // given - Review 의 memberId == 요청자 memberId
            Review review = ReviewFixture.defaultReview(); // memberId = DEFAULT_MEMBER_ID
            UUID selfMemberId = ReviewFixture.DEFAULT_MEMBER_ID;

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

            // when & then
            assertThatThrownBy(() -> reviewService.toggleLike(selfMemberId, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_SELF_LIKE_FORBIDDEN.getMessage());

            verify(reviewLikeRepository, never()).saveAndFlush(any());
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("좋아요 등록 시 saveAndFlush 가 1번만 호출된다")
        void toggleLike_register_savesOnce() {
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.countByReviewId(REVIEW_ID)).thenReturn(1L);
            when(reviewLikeRepository.existsByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(true);

            reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            verify(reviewLikeRepository, times(1)).saveAndFlush(any(ReviewLike.class));
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("좋아요 해제 시 delete 가 1번만 호출된다")
        void toggleLike_cancel_deletesOnce() {
            Review review = ReviewFixture.defaultReview();
            ReviewLike existing = ReviewLike.register(REVIEW_ID, OTHER_MEMBER_ID);

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.of(existing));
            when(reviewLikeRepository.countByReviewId(REVIEW_ID)).thenReturn(0L);
            when(reviewLikeRepository.existsByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(false);

            reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            verify(reviewLikeRepository, times(1)).delete(existing);
            verify(reviewLikeRepository, never()).saveAndFlush(any());
        }

        @Test
        @DisplayName("동시 요청으로 중복 INSERT 발생 시 예외를 무시하고 정상 응답한다")
        void toggleLike_concurrentDuplicate_ignoresException() {
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            // 동시 요청으로 UNIQUE 제약 위반 시뮬레이션
            when(reviewLikeRepository.saveAndFlush(any()))
                    .thenThrow(new DataIntegrityViolationException("uk_review_likes_review_member"));
            when(reviewLikeRepository.countByReviewId(REVIEW_ID)).thenReturn(1L);
            when(reviewLikeRepository.existsByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(true);

            // 예외가 전파되지 않고 정상 응답해야 한다
            ReviewLikeResponse result = reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            assertThat(result.isLiked()).isTrue();
            assertThat(result.getLikeCount()).isEqualTo(1L);
        }

        @Test
        @DisplayName("반환된 ReviewLikeResponse 에 reviewId 가 포함된다")
        void toggleLike_responseContainsReviewId() {
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.countByReviewId(REVIEW_ID)).thenReturn(1L);
            when(reviewLikeRepository.existsByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(true);

            ReviewLikeResponse result = reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            assertThat(result.getReviewId()).isEqualTo(REVIEW_ID);
        }
    }
}