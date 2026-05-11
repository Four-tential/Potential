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
import four_tential.potential.common.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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
    @Mock private ReviewCacheService reviewCacheService;
    @Mock private four_tential.potential.infra.ai.review.ReviewSummaryService reviewSummaryService;
    @Mock private RedissonClient redissonClient;
    @Mock private RLock rLock;

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
                10, BigInteger.valueOf(50000),
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
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reviewRepository.countByCourseId(COURSE_ID)).thenReturn(1L); // 첫 번째 후기

            // when
            ReviewResponse response = reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", List.of());

            // then
            assertThat(response.rating()).isEqualTo(5);
            assertThat(response.content()).isEqualTo("좋아요");
            verify(reviewRepository).save(any(Review.class));
            verify(reviewSummaryService).updateSummary(COURSE_ID, 5, "좋아요");
        }

        @Test
        @DisplayName("첫 번째 후기(count=1)이면 updateSummary 를 호출한다")
        void create_firstReview_callsUpdateSummary() {
            // given
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance attendance = attendedAttendance();

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reviewRepository.countByCourseId(COURSE_ID)).thenReturn(1L);

            // when
            reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", List.of());

            // then
            verify(reviewSummaryService).updateSummary(COURSE_ID, 5, "좋아요");
        }

        @Test
        @DisplayName("5의 배수 번째 후기(count=5)이면 updateSummary 를 호출한다")
        void create_fifthReview_callsUpdateSummary() {
            // given
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance attendance = attendedAttendance();

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reviewRepository.countByCourseId(COURSE_ID)).thenReturn(5L);

            // when
            reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", List.of());

            // then
            verify(reviewSummaryService).updateSummary(COURSE_ID, 5, "좋아요");
        }

        @Test
        @DisplayName("5의 배수 번째 후기(count=10)이면 updateSummary 를 호출한다")
        void create_tenthReview_callsUpdateSummary() {
            // given
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance attendance = attendedAttendance();

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reviewRepository.countByCourseId(COURSE_ID)).thenReturn(10L);

            // when
            reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", List.of());

            // then
            verify(reviewSummaryService).updateSummary(COURSE_ID, 5, "좋아요");
        }

        @Test
        @DisplayName("5의 배수가 아닌 중간 후기(count=3)이면 updateSummary 를 호출하지 않는다")
        void create_middleReview_doesNotCallUpdateSummary() {
            // given
            Order order = confirmedOrder();
            Course course = closedCourse(2);
            Attendance attendance = attendedAttendance();

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
            when(reviewRepository.countByCourseId(COURSE_ID)).thenReturn(3L);

            // when
            reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "좋아요", List.of());

            // then
            verify(reviewSummaryService, never()).updateSummary(any(), anyInt(), any());
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
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
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
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
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
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(absent));

            assertThatThrownBy(() -> reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", List.of()))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_NOT_ATTENDED.getMessage());
        }

        @Test
        @DisplayName("이미 후기를 작성했으면 ERR_ALREADY_REVIEWED 를 던진다")
        void create_alreadyReviewed_throwsException() {
            // 중복 검증이 주문 검증 직후로 이동 → courseRepository, attendanceRepository mock 불필요
            Order order = confirmedOrder();

            when(orderRepository.findOrderDetailsById(ORDER_ID, MEMBER_ID)).thenReturn(Optional.of(order));
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
            when(attendanceRepository.findByMemberIdAndCourseIdQuery(MEMBER_ID, COURSE_ID))
                    .thenReturn(Optional.of(attendance));
            when(reviewRepository.existsByOrderIdAndMemberId(ORDER_ID, MEMBER_ID)).thenReturn(false);
            when(reviewRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

            reviewService.create(MEMBER_ID, COURSE_ID, ORDER_ID, 5, "내용", null);

            verify(reviewImageRepository, never()).saveAll(any());
        }
    }

    @Nested
    @DisplayName("findAllByCourse() - 코스별 후기 목록 페이지 조회")
    class FindAllByCourseTest {

        @Test
        @DisplayName("해당 코스의 후기 목록을 페이지 단위로 반환한다")
        void findAllByCourse_success() {
            ReviewResponse stubResponse = new ReviewResponse(UUID.randomUUID(), MEMBER_ID, COURSE_ID, 5, "좋아요", List.of(), null, null);
            PageResponse<ReviewResponse> pageResponse = new PageResponse<>(List.of(stubResponse), 0, 1, 1L, 20, true);
            when(reviewCacheService.getCachedReviews(COURSE_ID, 0, 20)).thenReturn(pageResponse);

            PageResponse<ReviewResponse> result = reviewService.findAllByCourse(COURSE_ID, 0, 20);

            assertThat(result.content()).hasSize(1);
            assertThat(result.currentPage()).isEqualTo(0);
            assertThat(result.totalElements()).isEqualTo(1L);
            verify(reviewCacheService).getCachedReviews(COURSE_ID, 0, 20);
        }

        @Test
        @DisplayName("후기가 없으면 빈 페이지를 반환한다")
        void findAllByCourse_empty() {
            PageResponse<ReviewResponse> emptyPage = new PageResponse<>(List.of(), 0, 0, 0L, 20, true);
            when(reviewCacheService.getCachedReviews(COURSE_ID, 0, 20)).thenReturn(emptyPage);

            PageResponse<ReviewResponse> result = reviewService.findAllByCourse(COURSE_ID, 0, 20);

            assertThat(result.content()).isEmpty();
            assertThat(result.totalElements()).isEqualTo(0L);
        }

        @Test
        @DisplayName("캐싱은 ReviewCacheService에 위임한다 (self-invocation 방지)")
        void findAllByCourse_delegatesToCacheService() {
            PageResponse<ReviewResponse> emptyPage = new PageResponse<>(List.of(), 0, 0, 0L, 20, true);
            when(reviewCacheService.getCachedReviews(COURSE_ID, 0, 20)).thenReturn(emptyPage);

            reviewService.findAllByCourse(COURSE_ID, 0, 20);

            verify(reviewCacheService, times(1)).getCachedReviews(COURSE_ID, 0, 20);
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

            assertThat(result.rating()).isEqualTo(ReviewFixture.DEFAULT_RATING);
            assertThat(result.content()).isEqualTo(ReviewFixture.DEFAULT_CONTENT);
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

            assertThat(result.imageUrls()).hasSize(1);
            assertThat(result.imageUrls().get(0)).isEqualTo("https://cdn.test/img.jpg");
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

            assertThat(result.rating()).isEqualTo(3);
            assertThat(result.content()).isEqualTo("수정된 내용");
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

        @org.junit.jupiter.api.AfterEach
        void tearDown() {
            // 트랜잭션 컨텍스트 정리 (mockLock에서 initSynchronization 호출한 경우)
            if (TransactionSynchronizationManager.isSynchronizationActive()) {
                TransactionSynchronizationManager.clearSynchronization();
            }
        }

        private static final UUID OTHER_MEMBER_ID = UUID.randomUUID();

        // Redisson 락 공통 mock 설정
        // 단위 테스트 환경에서는 트랜잭션이 없으므로 직접 초기화
        private void mockLock() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any())).thenReturn(true);
            lenient().when(rLock.isHeldByCurrentThread()).thenReturn(true);
            TransactionSynchronizationManager.initSynchronization();
        }

        @Test
        @DisplayName("좋아요가 없으면 등록하고 liked=true 를 반환한다")
        void toggleLike_register_success() throws InterruptedException {
            mockLock();
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{1L, 1L});

            ReviewLikeResponse result = reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            assertThat(result.getLikeCount()).isEqualTo(1L);
            assertThat(result.isLiked()).isTrue();
            verify(reviewLikeRepository).save(any(ReviewLike.class));
        }

        @Test
        @DisplayName("이미 좋아요가 있으면 해제하고 liked=false 를 반환한다")
        void toggleLike_cancel_success() throws InterruptedException {
            mockLock();
            Review review = ReviewFixture.defaultReview();
            ReviewLike existing = ReviewLike.register(REVIEW_ID, OTHER_MEMBER_ID);

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.of(existing));
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{0L, 0L});

            ReviewLikeResponse result = reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            assertThat(result.getLikeCount()).isEqualTo(0L);
            assertThat(result.isLiked()).isFalse();
            verify(reviewLikeRepository).delete(existing);
        }

        @Test
        @DisplayName("후기가 없으면 ERR_REVIEW_NOT_FOUND 를 던진다")
        void toggleLike_reviewNotFound_throwsException() throws InterruptedException {
            mockLock();
            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.empty());

            assertThatThrownBy(() -> reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_REVIEW_NOT_FOUND.getMessage());

            verify(reviewLikeRepository, never()).save(any());
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("자기 자신의 후기에 좋아요 시 ERR_SELF_LIKE_FORBIDDEN 를 던진다")
        void toggleLike_selfLike_throwsException() throws InterruptedException {
            mockLock();
            Review review = ReviewFixture.defaultReview();
            UUID selfMemberId = ReviewFixture.DEFAULT_MEMBER_ID;

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));

            assertThatThrownBy(() -> reviewService.toggleLike(selfMemberId, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_SELF_LIKE_FORBIDDEN.getMessage());

            verify(reviewLikeRepository, never()).save(any());
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("좋아요 등록 시 save 가 1번만 호출된다")
        void toggleLike_register_savesOnce() throws InterruptedException {
            mockLock();
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{1L, 1L});

            reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            verify(reviewLikeRepository, times(1)).save(any(ReviewLike.class));
            verify(reviewLikeRepository, never()).delete(any());
        }

        @Test
        @DisplayName("좋아요 해제 시 delete 가 1번만 호출된다")
        void toggleLike_cancel_deletesOnce() throws InterruptedException {
            mockLock();
            Review review = ReviewFixture.defaultReview();
            ReviewLike existing = ReviewLike.register(REVIEW_ID, OTHER_MEMBER_ID);

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.of(existing));
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{0L, 0L});

            reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            verify(reviewLikeRepository, times(1)).delete(existing);
            verify(reviewLikeRepository, never()).save(any());
        }

        @Test
        @DisplayName("락 획득 실패 시 ERR_LIKE_LOCK_FAILED 를 던진다")
        void toggleLike_lockFailed_throwsException() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any())).thenReturn(false);

            assertThatThrownBy(() -> reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_LIKE_LOCK_FAILED.getMessage());

            // 락을 획득하지 못했으므로 unlock 호출 없어야 함
            verify(rLock, never()).unlock();
        }

        @Test
        @DisplayName("tryLock 중 InterruptedException 발생 시 ERR_LIKE_LOCK_FAILED 를 던진다")
        void toggleLike_interrupted_throwsException() throws InterruptedException {
            when(redissonClient.getLock(anyString())).thenReturn(rLock);
            when(rLock.tryLock(anyLong(), any())).thenThrow(new InterruptedException());

            assertThatThrownBy(() -> reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID))
                    .isInstanceOf(ServiceErrorException.class)
                    .hasMessage(ERR_LIKE_LOCK_FAILED.getMessage());

            // interrupt 상태가 복원되었는지 확인
            assertThat(Thread.currentThread().isInterrupted()).isTrue();
            // 인터럽트 상태 정리
            Thread.interrupted();
        }

        @Test
        @DisplayName("반환된 ReviewLikeResponse 에 reviewId 가 포함된다")
        void toggleLike_responseContainsReviewId() throws InterruptedException {
            mockLock();
            Review review = ReviewFixture.defaultReview();

            when(reviewRepository.findById(REVIEW_ID)).thenReturn(Optional.of(review));
            when(reviewLikeRepository.findByReviewIdAndMemberId(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(Optional.empty());
            when(reviewLikeRepository.findCountAndLikedStatus(REVIEW_ID, OTHER_MEMBER_ID))
                    .thenReturn(new Object[]{1L, 1L});

            ReviewLikeResponse result = reviewService.toggleLike(OTHER_MEMBER_ID, REVIEW_ID);

            assertThat(result.getReviewId()).isEqualTo(REVIEW_ID);
        }
    }

    @Nested
    @DisplayName("getSummary() - 코스 후기 요약 조회")
    class GetSummaryTest {

        @Test
        @DisplayName("요약이 존재하면 courseId 와 summary 를 반환한다")
        void getSummary_returnsSummary() {
            // given
            Course course = closedCourse(2);
            try {
                java.lang.reflect.Field f = Course.class.getDeclaredField("summary");
                f.setAccessible(true);
                f.set(course, "긍정적인 후기 요약입니다.");
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            // when
            var response = reviewService.getSummary(COURSE_ID);

            // then
            assertThat(response.courseId()).isEqualTo(COURSE_ID);
            assertThat(response.summary()).isEqualTo("긍정적인 후기 요약입니다.");
        }

        @Test
        @DisplayName("요약이 null 이면 summary 가 null 로 반환된다")
        void getSummary_returnsNullSummary() {
            // given
            Course course = closedCourse(2);
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.of(course));

            // when
            var response = reviewService.getSummary(COURSE_ID);

            // then
            assertThat(response.courseId()).isEqualTo(COURSE_ID);
            assertThat(response.summary()).isNull();
        }

        @Test
        @DisplayName("코스가 존재하지 않으면 ERR_REVIEW_NOT_FOUND 를 던진다")
        void getSummary_throwsWhenCourseNotFound() {
            // given
            when(courseRepository.findById(COURSE_ID)).thenReturn(Optional.empty());

            // when & then
            assertThatThrownBy(() -> reviewService.getSummary(COURSE_ID))
                    .isInstanceOf(ServiceErrorException.class);
        }
    }
}