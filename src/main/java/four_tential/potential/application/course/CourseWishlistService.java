package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_wishlist.CourseWishlist;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.presentation.course.model.response.CourseWishlistResponse;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.*;

@Service
@RequiredArgsConstructor
public class CourseWishlistService {

    private final CourseRepository courseRepository;
    private final CourseWishlistRepository courseWishlistRepository;

    @Transactional(readOnly = true)
    public PageResponse<WishlistCourseItem> getMyWishlistCourses(UUID memberId, int page, int size) {
        return PageResponse.register(
                courseWishlistRepository.findWishlistCourses(memberId, PageRequest.of(page, size))
                        .map(WishlistCourseItem::register)
        );
    }

    @Transactional
    public CourseWishlistResponse addWishlist(UUID memberId, UUID courseId) {
        Course course = courseRepository.findById(courseId)
                .filter(c -> c.getStatus() == CourseStatus.OPEN)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));
        if (courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId)) {
            throw new ServiceErrorException(ERR_ALREADY_WISHLISTED);
        }
        courseWishlistRepository.save(CourseWishlist.register(memberId, courseId));
        return new CourseWishlistResponse(courseId, true);
    }

    @Transactional
    public CourseWishlistResponse removeWishlist(UUID memberId, UUID courseId) {
        courseWishlistRepository.deleteByMemberIdAndCourseIdQuery(memberId, courseId);
        return new CourseWishlistResponse(courseId, false);
    }
}
