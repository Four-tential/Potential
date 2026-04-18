package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CourseWishlistService {

    private final CourseWishlistRepository courseWishlistRepository;

    @Transactional(readOnly = true)
    public PageResponse<WishlistCourseItem> getMyWishlistCourses(UUID memberId, int page, int size) {
        return PageResponse.register(
                courseWishlistRepository.findWishlistCourses(memberId, PageRequest.of(page, size))
        );
    }
}
