package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseListQueryResult;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseSearchCondition;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.course_image.CourseImage;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalAction;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalHistory;
import four_tential.potential.domain.course.course_approval_history.CourseApprovalHistoryRepository;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.presentation.course.model.request.CourseRequestActionRequest;
import four_tential.potential.presentation.course.model.request.CreateCourseRequestRequest;
import four_tential.potential.presentation.course.model.response.CourseRequestActionResponse;
import four_tential.potential.presentation.course.model.response.CourseDetailInstructorInfo;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import four_tential.potential.presentation.course.model.response.CourseStudentItem;
import four_tential.potential.presentation.course.model.response.CreateCourseRequestResponse;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_INSTRUCTOR;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_MEMBER;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseImageRepository courseImageRepository;
    private final CourseApprovalHistoryRepository courseApprovalHistoryRepository;
    private final CourseCategoryRepository courseCategoryRepository;
    private final CourseWishlistRepository courseWishlistRepository;
    private final InstructorMemberRepository instructorMemberRepository;
    private final MemberRepository memberRepository;
    private final ReviewRepository reviewRepository;
    private final OrderRepository orderRepository;

    @Transactional(readOnly = true)
    public PageResponse<CourseListItem> getCourses(CourseSearchCondition condition, UUID memberId, Pageable pageable) {
        Page<CourseListQueryResult> results = courseRepository.findCourses(condition, pageable);

        List<UUID> courseIds = results.getContent().stream()
                .map(CourseListQueryResult::courseId)
                .toList();

        Set<UUID> wishlistedIds = (memberId != null && !courseIds.isEmpty())
                ? new HashSet<>(courseWishlistRepository.findWishlistedCourseIds(memberId, courseIds))
                : Collections.emptySet();

        Page<CourseListItem> mapped = results.map(result ->
                CourseListItem.register(result, wishlistedIds.contains(result.courseId()))
        );

        return PageResponse.register(mapped);
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(UUID courseId, UUID memberId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getStatus() != CourseStatus.PREPARATION)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        CourseCategory category = courseCategoryRepository.findById(course.getCourseCategoryId())
                .orElseThrow(() -> new ServiceErrorException(ERR_CATEGORY_NOT_FOUND));

        InstructorMember instructorMember = instructorMemberRepository.findById(course.getMemberInstructorId())
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Member instructorMemberInfo = memberRepository.findById(instructorMember.getMemberId())
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_MEMBER));

        double instructorAvgRating = Optional.ofNullable(
                reviewRepository.findAverageRatingByMemberInstructorId(instructorMember.getId())
        ).orElse(0.0);

        double courseAvgRating = Optional.ofNullable(
                reviewRepository.findAverageRatingByCourseId(courseId)
        ).orElse(0.0);

        long reviewCount = reviewRepository.countByCourseId(courseId);

        boolean isWishlisted = memberId != null
                && courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId);

        List<String> imageUrls = course.getImages().stream()
                .sorted(Comparator.comparing(image -> image.getId()))
                .map(image -> image.getImageUrl())
                .toList();

        return new CourseDetailResponse(
                course.getId(),
                course.getTitle(),
                course.getDescription(),
                category.getCode(),
                category.getName(),
                new CourseDetailInstructorInfo(
                        instructorMemberInfo.getId(),
                        instructorMemberInfo.getName(),
                        instructorMemberInfo.getProfileImageUrl(),
                        instructorAvgRating
                ),
                imageUrls,
                course.getAddressMain(),
                course.getAddressDetail(),
                course.getPrice(),
                course.getCapacity(),
                course.getConfirmCount(),
                course.getStatus(),
                course.getLevel(),
                course.getOrderOpenAt(),
                course.getOrderCloseAt(),
                course.getStartAt(),
                course.getEndAt(),
                courseAvgRating,
                reviewCount,
                isWishlisted
        );
    }

    @Transactional(readOnly = true)
    public PageResponse<InstructorCourseListItem> getInstructorCourses(UUID instructorId, Pageable pageable) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(instructorId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Page<InstructorCourseListItem> courses =
                courseRepository.findCoursesByInstructorMemberId(instructorMember.getId(), pageable)
                        .map(InstructorCourseListItem::register);

        return PageResponse.register(courses);
    }


    @Transactional(readOnly = true)
    public PageResponse<InstructorCourseListItem> getMyInstructorCourses(UUID memberId, Pageable pageable) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Page<InstructorCourseListItem> courses =
                courseRepository.findMyCoursesByInstructorMemberId(instructorMember.getId(), pageable)
                        .map(InstructorCourseListItem::register);

        return PageResponse.register(courses);
    }

    @Transactional(readOnly = true)
    public PageResponse<CourseStudentItem> getCourseStudents(UUID courseId, UUID memberId, Pageable pageable) {
        // 강사 본인 검증
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(instructor -> instructor.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        // 코스 존재 여부 검증
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        // 본인 코스 여부 검증
        if (!course.getMemberInstructorId().equals(instructorMember.getId())) {
            throw new ServiceErrorException(ERR_FORBIDDEN_COURSE);
        }

        // PREPARATION 코스는 수강생 조회 불가 (개설 승인 대기 중 - CONFIRMED 주문 없음)
        if (course.getStatus() == CourseStatus.PREPARATION) {
            throw new ServiceErrorException(ERR_COURSE_IN_PREPARATION);
        }

        Page<CourseStudentItem> students = orderRepository
                .findConfirmedStudentsByCourseId(courseId, pageable)
                .map(result -> CourseStudentItem.register(result));

        return PageResponse.register(students);
    }

    @Transactional
    public CreateCourseRequestResponse createCourseRequest(UUID memberId, CreateCourseRequestRequest request) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        CourseCategory category = courseCategoryRepository.findByCode(instructorMember.getCategoryCode())
                .orElseThrow(() -> new ServiceErrorException(ERR_CATEGORY_NOT_FOUND));

        Course course = Course.register(
                category.getId(),
                instructorMember.getId(),
                request.title(),
                request.description(),
                request.addressMain(),
                request.addressDetail(),
                request.capacity(),
                request.price(),
                request.level(),
                request.orderOpenAt(),
                request.orderCloseAt(),
                request.startAt(),
                request.endAt()
        );
        courseRepository.save(course);

        if (request.imageUrls() != null && !request.imageUrls().isEmpty()) {
            List<CourseImage> images = request.imageUrls().stream()
                    .map(url -> CourseImage.register(course, url))
                    .collect(Collectors.toList());
            courseImageRepository.saveAll(images);
        }

        return CreateCourseRequestResponse.register(course, category.getCode());
    }

    @Transactional
    public void deleteCourseRequest(UUID memberId, UUID courseId) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        if (!course.getMemberInstructorId().equals(instructorMember.getId())) {
            throw new ServiceErrorException(ERR_FORBIDDEN_COURSE_DELETE);
        }

        if (course.getStatus() != CourseStatus.PREPARATION) {
            throw new ServiceErrorException(ERR_CANNOT_DELETE_COURSE_REQUEST);
        }

        courseRepository.delete(course);
    }

    @Transactional
    public CourseRequestActionResponse handleCourseRequest(UUID courseId, CourseRequestActionRequest request) {
        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        if (course.getStatus() != CourseStatus.PREPARATION) {
            throw new ServiceErrorException(ERR_COURSE_NOT_IN_PREPARATION);
        }

        if (request.action() == CourseApprovalAction.REJECT) {
            if (request.rejectReason() == null || request.rejectReason().isBlank()) {
                throw new ServiceErrorException(ERR_REJECT_REASON_REQUIRED);
            }
            course.reject(request.rejectReason());
            courseApprovalHistoryRepository.save(
                    CourseApprovalHistory.register(courseId, CourseApprovalAction.REJECT, request.rejectReason())
            );
        } else if (request.action() == CourseApprovalAction.APPROVE) {
            course.confirm();
            courseApprovalHistoryRepository.save(
                    CourseApprovalHistory.register(courseId, CourseApprovalAction.APPROVE, null)
            );
        }

        return CourseRequestActionResponse.from(course);
    }

    @Transactional
    public void reapplyCourseRequest(UUID memberId, UUID courseId) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        if (!course.getMemberInstructorId().equals(instructorMember.getId())) {
            throw new ServiceErrorException(ERR_FORBIDDEN_COURSE);
        }

        course.reapply();
    }
}
