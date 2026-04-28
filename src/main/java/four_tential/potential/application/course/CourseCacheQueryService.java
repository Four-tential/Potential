package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseDetailQueryResult;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.presentation.course.model.response.CourseDetailInstructorInfo;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static four_tential.potential.common.exception.domain.CourseExceptionEnum.ERR_NOT_FOUND_COURSE;
import static four_tential.potential.infra.redis.RedisConstants.COURSE_DETAIL_CACHE;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CourseCacheQueryService {

    private final CourseRepository courseRepository;
    private final CourseImageRepository courseImageRepository;

    @Cacheable(cacheNames = COURSE_DETAIL_CACHE, key = "#courseId")
    public CourseDetailResponse getCourseDetailCache(UUID courseId) {
        CourseDetailQueryResult detail = courseRepository.findCourseDetail(courseId)
                .orElseThrow(() -> new ServiceErrorException(ERR_NOT_FOUND_COURSE));

        List<String> imageUrls = courseImageRepository.findImageUrlsByCourseId(courseId);

        return new CourseDetailResponse(
                detail.courseId(), detail.title(), detail.description(),
                detail.categoryCode(), detail.categoryName(),
                new CourseDetailInstructorInfo(
                        detail.instructorMemberId(), detail.instructorName(),
                        detail.instructorProfileImageUrl(), detail.instructorAvgRating()
                ),
                imageUrls, detail.addressMain(), detail.addressDetail(),
                detail.price(), detail.capacity(), detail.confirmCount(),
                detail.status(), detail.level(),
                detail.orderOpenAt(), detail.orderCloseAt(),
                detail.startAt(), detail.endAt(),
                detail.courseAvgRating(), detail.reviewCount(),
                false
        );
    }
}
