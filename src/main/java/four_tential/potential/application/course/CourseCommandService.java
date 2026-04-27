package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.course_image.CourseImage;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMemberStatus;
import four_tential.potential.presentation.course.model.request.CreateCourseRequestRequest;
import four_tential.potential.presentation.course.model.request.UpdateCourseRequest;
import four_tential.potential.presentation.course.model.response.CreateCourseRequestResponse;
import four_tential.potential.presentation.course.model.response.UpdateCourseResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static four_tential.potential.infra.redis.RedisConstants.INSTRUCTOR_PROFILE_CACHE;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_INSTRUCTOR;

@Service
@RequiredArgsConstructor
public class CourseCommandService {

    private final CourseRepository courseRepository;
    private final CourseImageRepository courseImageRepository;
    private final CourseCategoryRepository courseCategoryRepository;
    private final CourseWishlistRepository courseWishlistRepository;
    private final InstructorMemberRepository instructorMemberRepository;

    @CacheEvict(cacheNames = INSTRUCTOR_PROFILE_CACHE, key = "#memberId")
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

    @CacheEvict(cacheNames = INSTRUCTOR_PROFILE_CACHE, key = "#memberId")
    @Transactional
    public UpdateCourseResponse updateCourse(UUID memberId, UUID courseId, UpdateCourseRequest request) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        if (!course.getMemberInstructorId().equals(instructorMember.getId())) {
            throw new ServiceErrorException(ERR_FORBIDDEN_COURSE_MODIFY);
        }

        CourseStatus status = course.getStatus();

        if (status == CourseStatus.CLOSED || status == CourseStatus.CANCELLED) {
            throw new ServiceErrorException(ERR_CANNOT_MODIFY_COURSE);
        }

        if (status == CourseStatus.OPEN && request.hasPrepOnlyFields()) {
            throw new ServiceErrorException(ERR_IMMUTABLE_FIELD_IN_OPEN);
        }

        course.updateInfo(request.title(), request.description());

        if (status == CourseStatus.PREPARATION || status == CourseStatus.REJECTED) {
            course.updateInfoInPreparation(
                    request.price(), request.capacity(), request.level(),
                    request.addressMain(), request.addressDetail(),
                    request.orderOpenAt(), request.orderCloseAt(),
                    request.startAt(), request.endAt()
            );
        }

        if (request.imageUrls() != null) {
            course.clearImages();
            if (!request.imageUrls().isEmpty()) {
                courseImageRepository.saveAll(
                        request.imageUrls().stream()
                                .map(url -> CourseImage.register(course, url))
                                .toList()
                );
            }
        }

        return UpdateCourseResponse.from(course);
    }

    @CacheEvict(cacheNames = INSTRUCTOR_PROFILE_CACHE, key = "#memberId")
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

    @CacheEvict(cacheNames = INSTRUCTOR_PROFILE_CACHE, key = "#memberId")
    @Transactional
    public void closeCourse(UUID memberId, UUID courseId) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        if (!course.getMemberInstructorId().equals(instructorMember.getId())) {
            throw new ServiceErrorException(ERR_FORBIDDEN_COURSE_CLOSE);
        }

        course.close();

        courseWishlistRepository.deleteByCourseId(courseId);
    }

    @Transactional
    public void reapplyCourseRequest(UUID memberId, UUID courseId) {
        InstructorMember instructorMember = instructorMemberRepository.findByMemberId(memberId)
                .filter(im -> im.getStatus() == InstructorMemberStatus.APPROVED)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_INSTRUCTOR));

        Course course = courseRepository.findById(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        if (!course.getMemberInstructorId().equals(instructorMember.getId())) {
            throw new ServiceErrorException(ERR_FORBIDDEN_COURSE_MODIFY);
        }

        course.reapply();
    }
}
