package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseListQueryResult;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseSearchCondition;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.domain.order.OrderRepository;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import four_tential.potential.presentation.course.model.response.CourseStudentItem;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_INSTRUCTOR;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseQueryService {

    private final CourseRepository courseRepository;
    private final CourseWishlistRepository courseWishlistRepository;
    private final InstructorMemberRepository instructorMemberRepository;
    private final OrderRepository orderRepository;
    private final CourseCacheQueryService courseCacheQueryService;

    public PageResponse<CourseListItem> getCourses(CourseSearchCondition condition, UUID memberId, Pageable pageable) {
        Page<CourseListQueryResult> results = courseRepository.findCourses(condition, pageable);

        List<UUID> courseIds = results.getContent().stream()
                .map(courseListQueryResult -> courseListQueryResult.courseId())
                .toList();

        Set<UUID> wishlistedIds = (memberId != null && !courseIds.isEmpty())
                ? new HashSet<>(courseWishlistRepository.findWishlistedCourseIds(memberId, courseIds))
                : Collections.emptySet();

        Page<CourseListItem> mapped = results.map(result ->
                CourseListItem.register(result, wishlistedIds.contains(result.courseId()))
        );

        return PageResponse.register(mapped);
    }

    public CourseDetailResponse getCourseDetail(UUID courseId, UUID memberId) {
        CourseDetailResponse cached = courseCacheQueryService.getCourseDetailCache(courseId);

        boolean isWishlisted = memberId != null
                && courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId);

        return new CourseDetailResponse(
                cached.courseId(), cached.title(), cached.description(),
                cached.categoryCode(), cached.categoryName(), cached.instructor(),
                cached.images(), cached.addressMain(), cached.addressDetail(),
                cached.price(), cached.capacity(), cached.confirmCount(),
                cached.status(), cached.level(),
                cached.orderOpenAt(), cached.orderCloseAt(),
                cached.startAt(), cached.endAt(),
                cached.averageRating(), cached.reviewCount(),
                isWishlisted
        );
    }

    public PageResponse<InstructorCourseListItem> getInstructorCourses(UUID instructorId, Pageable pageable) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(instructorId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Page<InstructorCourseListItem> courses =
                courseRepository.findCoursesByInstructorMemberId(instructorMember.getId(), pageable)
                        .map(InstructorCourseListItem::register);

        return PageResponse.register(courses);
    }

    public PageResponse<InstructorCourseListItem> getMyInstructorCourses(UUID memberId, Pageable pageable) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Page<InstructorCourseListItem> courses =
                courseRepository.findMyCoursesByInstructorMemberId(instructorMember.getId(), pageable)
                        .map(InstructorCourseListItem::register);

        return PageResponse.register(courses);
    }

    public PageResponse<CourseStudentItem> getCourseStudents(UUID courseId, UUID memberId, Pageable pageable) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(instructor -> instructor.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        if (!course.getMemberInstructorId().equals(instructorMember.getId())) {
            throw new ServiceErrorException(ERR_FORBIDDEN_COURSE);
        }

        if (course.getStatus() == CourseStatus.PREPARATION) {
            throw new ServiceErrorException(ERR_COURSE_IN_PREPARATION);
        }

        Page<CourseStudentItem> students = orderRepository
                .findConfirmedStudentsByCourseId(courseId, pageable)
                .map(result -> CourseStudentItem.register(result));

        return PageResponse.register(students);
    }

    public Course getCourseEntity(UUID courseId) {
        return courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));
    }
}
