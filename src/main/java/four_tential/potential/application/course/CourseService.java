package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseListQueryResult;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_category.CourseCategory;
import four_tential.potential.domain.course.course_category.CourseCategoryRepository;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.member.instructor_member.InstructorMember;
import four_tential.potential.domain.member.instructor_member.InstructorMemberRepository;
import four_tential.potential.domain.member.member.Member;
import four_tential.potential.domain.member.member.MemberRepository;
import four_tential.potential.domain.review.review.ReviewRepository;
import four_tential.potential.presentation.course.model.request.CourseSearchRequest;
import four_tential.potential.presentation.course.model.response.CourseDetailInstructorInfo;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import four_tential.potential.presentation.course.model.response.CourseListItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_CATEGORY;
import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_COURSE;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_INSTRUCTOR;
import static four_tential.potential.common.exception.domain.MemberExceptionEnum.ERR_NOT_FOUND_MEMBER;

@Service
@RequiredArgsConstructor
public class CourseService {

    private final CourseRepository courseRepository;
    private final CourseCategoryRepository courseCategoryRepository;
    private final CourseWishlistRepository courseWishlistRepository;
    private final InstructorMemberRepository instructorMemberRepository;
    private final MemberRepository memberRepository;
    private final ReviewRepository reviewRepository;

    @Transactional(readOnly = true)
    public PageResponse<CourseListItem> getCourses(CourseSearchRequest condition, UUID memberId, Pageable pageable) {
        Page<CourseListQueryResult> results = courseRepository.findCourses(condition, pageable);

        List<UUID> courseIds = results.getContent().stream()
                .map(CourseListQueryResult::courseId)
                .toList();

        Set<UUID> wishlistedIds = (memberId != null && !courseIds.isEmpty())
                ? new HashSet<>(courseWishlistRepository.findWishlistedCourseIds(memberId, courseIds))
                : Collections.emptySet();

        Page<CourseListItem> mapped = results.map(result ->
                CourseListItem.from(result, wishlistedIds.contains(result.courseId()))
        );

        return PageResponse.register(mapped);
    }

    @Transactional(readOnly = true)
    public CourseDetailResponse getCourseDetail(UUID courseId, UUID memberId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getStatus() != CourseStatus.PREPARATION)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        CourseCategory category = courseCategoryRepository.findById(course.getCourseCategoryId())
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_CATEGORY));

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
                course.getOrderOpenAt(),
                course.getOrderCloseAt(),
                course.getStartAt(),
                course.getEndAt(),
                courseAvgRating,
                reviewCount,
                isWishlisted
        );
    }
}
