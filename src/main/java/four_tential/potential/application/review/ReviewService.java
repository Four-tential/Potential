package four_tential.potential.application.review;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.attendance.Attendance;
import four_tential.potential.domain.attendance.AttendanceRepository;
import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.order.Order;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.order.OrderStatus;
import four_tential.potential.domain.review.review.Review;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.domain.review.review_image.ReviewImage;
import four_tential.potential.domain.review.review_image.ReviewImageRepository;
import four_tential.potential.domain.review.review_like.ReviewLike;
import four_tential.potential.domain.review.review_like.ReviewLikeRepository;
import four_tential.potential.presentation.review.dto.response.ReviewLikeResponse;
import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.presentation.review.dto.response.ReviewResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import org.springframework.dao.DataIntegrityViolationException;
import java.util.Collections;
import java.util.Optional;
import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.ReviewExceptionEnum.*;
import static four_tential.potential.common.exception.domain.OrderExceptionEnum.*;

@Service
@RequiredArgsConstructor
public class ReviewService {

    private static final int REVIEW_PERIOD_DAYS = 7;

    private final ReviewRepository reviewRepository;
    private final ReviewImageRepository reviewImageRepository;
    private final CourseRepository courseRepository;
    private final OrderRepository orderRepository;
    private final AttendanceRepository attendanceRepository;
    private final ReviewLikeRepository reviewLikeRepository;
    private final ReviewCacheService reviewCacheService;

    // 후기 작성
    @Transactional
    public ReviewResponse create(UUID memberId, UUID courseId, UUID orderId, int rating, String content, List<String> imageUrls) {

        // 주문 조회 및 검증 (본인 주문 여부 동시 확인)
        Order order = orderRepository.findOrderDetailsById(orderId, memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_ORDER));

        // 예약 확정 상태 검증
        if (order.getStatus() != OrderStatus.CONFIRMED) {
            throw new ServiceErrorException(ERR_ORDER_NOT_CONFIRMED);
        }

        // 주문-코스 정합성 검증
        if (!order.getCourseId().equals(courseId)) {
            throw new ServiceErrorException(ERR_NOT_FOUND_ORDER);
        }

        // 코스 조회 및 검증
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_REVIEW_NOT_FOUND));

        // 코스 종료 상태 검증
        if (course.getStatus() != CourseStatus.CLOSED) {
            throw new ServiceErrorException(ERR_COURSE_NOT_CLOSED);
        }

        // 후기 작성 기간 검증 (종료 후 7일 이내)
        if (LocalDateTime.now().isAfter(course.getEndAt().plusDays(REVIEW_PERIOD_DAYS))) {
            throw new ServiceErrorException(ERR_REVIEW_PERIOD_EXPIRED);
        }

        // 출석 여부 검증
        Attendance attendance = attendanceRepository.findByMemberIdAndCourseIdQuery(memberId, courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_ATTENDED));

        if (attendance.getStatus() != AttendanceStatus.ATTEND) {
            throw new ServiceErrorException(ERR_NOT_ATTENDED);
        }

        // 중복 후기 검증
        if (reviewRepository.existsByOrderIdAndMemberId(orderId, memberId)) {
            throw new ServiceErrorException(ERR_ALREADY_REVIEWED);
        }

        // 후기 저장
        Review review = Review.register(memberId, courseId, orderId, rating, content);
        reviewRepository.save(review);

        reviewCacheService.evictAll(); // 후기 작성 시 캐시 무효화

        // 이미지 저장
        List<ReviewImage> images = saveImages(review, imageUrls);

        return ReviewResponse.of(review, images);
    }

    // 코스별 후기 목록 페이지 조회
    // - 캐싱은 ReviewCacheService에 위임 (self-invocation 방지)
    // - PageResponse<ReviewResponse> 단위로 캐싱 (record 타입으로 역직렬화 안정성 확보)
    @Transactional(readOnly = true)
    public PageResponse<ReviewResponse> findAllByCourse(UUID courseId, int page, int size) {
        return reviewCacheService.getCachedReviews(courseId, page, size);
    }

    // 후기 단건 조회
    @Transactional(readOnly = true)
    public ReviewResponse findById(UUID reviewId) {
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ServiceErrorException(ERR_REVIEW_NOT_FOUND));

        List<ReviewImage> images = reviewImageRepository.findAllByReviewId(reviewId);
        return ReviewResponse.of(review, images);
    }

    // 후기 수정
    @Transactional
    public ReviewResponse update(UUID memberId, UUID reviewId, int rating, String content, List<String> imageUrls) {
        // 본인 후기 검증
        Review review = reviewRepository.findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_REVIEW_FORBIDDEN));

        // 후기 수정 기간 검증
        Course course = courseRepository.findById(review.getCourseId())
                .orElseThrow(() -> new ServiceErrorException(ERR_REVIEW_NOT_FOUND));

        if (LocalDateTime.now().isAfter(course.getEndAt().plusDays(REVIEW_PERIOD_DAYS))) {
            throw new ServiceErrorException(ERR_REVIEW_PERIOD_EXPIRED);
        }

        review.update(rating, content);

        // 기존 이미지 삭제 후 재저장
        reviewImageRepository.deleteAllByReviewId(reviewId);
        List<ReviewImage> images = saveImages(review, imageUrls);
        reviewCacheService.evictAll(); // 후기 수정 시 캐시 무효화

        return ReviewResponse.of(review, images);
    }

    // 후기 삭제
    @Transactional
    public void delete(UUID memberId, UUID reviewId) {
        Review review = reviewRepository.findByIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new ServiceErrorException(ERR_REVIEW_FORBIDDEN));

        reviewImageRepository.deleteAllByReviewId(reviewId);
        reviewRepository.delete(review);
        reviewCacheService.evictAll(); // 후기 삭제 시 캐시 무효화
    }

    // 후기 좋아요 토글 (등록 / 해제)
    @Transactional
    public ReviewLikeResponse toggleLike(UUID memberId, UUID reviewId) {

        // 후기 존재 여부 확인
        Review review = reviewRepository.findById(reviewId)
                .orElseThrow(() -> new ServiceErrorException(ERR_REVIEW_NOT_FOUND));

        // 자기 자신 후기 좋아요 방지
        if (review.getMemberId().equals(memberId)) {
            throw new ServiceErrorException(ERR_SELF_LIKE_FORBIDDEN);
        }

        // 이미 좋아요 -> 해제 / 없으면 -> 등록
        Optional<ReviewLike> existing = reviewLikeRepository.findByReviewIdAndMemberId(reviewId, memberId);
        if (existing.isPresent()) {
            reviewLikeRepository.delete(existing.get());
        } else {
            try {
                // saveAndFlush: 즉시 DB 반영으로 UNIQUE 위반을 트랜잭션 내에서 바로 감지
                reviewLikeRepository.saveAndFlush(ReviewLike.register(reviewId, memberId));
            } catch (DataIntegrityViolationException e) {
                // 동시 요청으로 중복 INSERT 발생 -> 이미 좋아요된 상태이므로 무시
            }
        }

        long likeCount = reviewLikeRepository.countByReviewId(reviewId);
        boolean liked  = reviewLikeRepository.existsByReviewIdAndMemberId(reviewId, memberId);

        return ReviewLikeResponse.of(reviewId, likeCount, liked);
    }

    // 이미지 저장 공통 로직
    private List<ReviewImage> saveImages(Review review, List<String> imageUrls) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return Collections.emptyList();
        }
        List<ReviewImage> images = imageUrls.stream()
                .map(url -> ReviewImage.register(review, url))
                .toList();
        return reviewImageRepository.saveAll(images);
    }
}