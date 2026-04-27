package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseSearchCondition;
import four_tential.potential.presentation.course.model.request.CourseRequestActionRequest;
import four_tential.potential.presentation.course.model.request.CreateCourseRequestRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseRequest;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import four_tential.potential.presentation.course.model.response.CourseRequestActionResponse;
import four_tential.potential.presentation.course.model.response.CourseStudentItem;
import four_tential.potential.presentation.course.model.response.CourseWishlistResponse;
import four_tential.potential.presentation.course.model.response.CreateCourseRequestResponse;
import four_tential.potential.presentation.course.model.response.InstructorCourseListItem;
import four_tential.potential.presentation.course.model.response.UpdateCourseResponse;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CourseFacade {

    private final CourseQueryService courseQueryService;
    private final CourseCommandService courseCommandService;
    private final CourseWishlistService courseWishlistService;
    private final CourseApprovalService courseApprovalService;

    public PageResponse<CourseListItem> getCourses(CourseSearchCondition condition, UUID memberId, Pageable pageable) {
        return courseQueryService.getCourses(condition, memberId, pageable);
    }

    public CourseDetailResponse getCourseDetail(UUID courseId, UUID memberId) {
        return courseQueryService.getCourseDetail(courseId, memberId);
    }

    public PageResponse<InstructorCourseListItem> getInstructorCourses(UUID instructorId, Pageable pageable) {
        return courseQueryService.getInstructorCourses(instructorId, pageable);
    }

    public PageResponse<InstructorCourseListItem> getMyInstructorCourses(UUID memberId, Pageable pageable) {
        return courseQueryService.getMyInstructorCourses(memberId, pageable);
    }

    public PageResponse<CourseStudentItem> getCourseStudents(UUID courseId, UUID memberId, Pageable pageable) {
        return courseQueryService.getCourseStudents(courseId, memberId, pageable);
    }

    public Course getCourseEntity(UUID courseId) {
        return courseQueryService.getCourseEntity(courseId);
    }

    public CreateCourseRequestResponse createCourseRequest(UUID memberId, CreateCourseRequestRequest request) {
        return courseCommandService.createCourseRequest(memberId, request);
    }

    public UpdateCourseResponse updateCourse(UUID memberId, UUID courseId, UpdateCourseRequest request) {
        return courseCommandService.updateCourse(memberId, courseId, request);
    }

    public void deleteCourseRequest(UUID memberId, UUID courseId) {
        courseCommandService.deleteCourseRequest(memberId, courseId);
    }

    public void closeCourse(UUID memberId, UUID courseId) {
        courseCommandService.closeCourse(memberId, courseId);
    }

    public void reapplyCourseRequest(UUID memberId, UUID courseId) {
        courseCommandService.reapplyCourseRequest(memberId, courseId);
    }

    public PageResponse<WishlistCourseItem> getMyWishlistCourses(UUID memberId, int page, int size) {
        return courseWishlistService.getMyWishlistCourses(memberId, page, size);
    }

    public CourseWishlistResponse addWishlist(UUID memberId, UUID courseId) {
        return courseWishlistService.addWishlist(memberId, courseId);
    }

    public CourseWishlistResponse removeWishlist(UUID memberId, UUID courseId) {
        return courseWishlistService.removeWishlist(memberId, courseId);
    }

    public CourseRequestActionResponse handleCourseRequest(UUID courseId, CourseRequestActionRequest request) {
        return courseApprovalService.courseRequest(courseId, request);
    }
}
