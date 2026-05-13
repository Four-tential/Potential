package four_tential.potential.application.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.CourseDetailQueryResult;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_image.CourseImageRepository;
import four_tential.potential.domain.course.fixture.CourseCategoryFixture;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.presentation.course.model.response.CourseDetailResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseCacheQueryServiceTest {

    @Mock private CourseRepository courseRepository;
    @Mock private CourseImageRepository courseImageRepository;

    @InjectMocks
    private CourseCacheQueryService courseCacheQueryService;

    @Test
    @DisplayName("코스 상세 캐시 조회 성공 - 모든 필드가 올바르게 매핑된다")
    void getCourseDetailCache_success_allFieldsMapped() {
        UUID courseId = UUID.randomUUID();
        UUID instructorMemberId = UUID.randomUUID();
        CourseDetailQueryResult detail = sampleDetail(courseId, instructorMemberId);
        List<String> imageUrls = List.of("https://cdn.example.com/img1.jpg", "https://cdn.example.com/img2.jpg");

        given(courseRepository.findCourseDetail(courseId)).willReturn(Optional.of(detail));
        given(courseImageRepository.findImageUrlsByCourseId(courseId)).willReturn(imageUrls);

        CourseDetailResponse response = courseCacheQueryService.getCourseDetailCache(courseId);

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.title()).isEqualTo(CourseFixture.DEFAULT_TITLE);
        assertThat(response.description()).isEqualTo(CourseFixture.DEFAULT_DESCRIPTION);
        assertThat(response.categoryCode()).isEqualTo(CourseCategoryFixture.DEFAULT_CODE);
        assertThat(response.categoryName()).isEqualTo(CourseCategoryFixture.DEFAULT_NAME);
        assertThat(response.instructor().memberId()).isEqualTo(instructorMemberId);
        assertThat(response.instructor().name()).isEqualTo("강사이름");
        assertThat(response.instructor().profileImageUrl()).isEqualTo("https://cdn.example.com/profile.jpg");
        assertThat(response.instructor().averageRating()).isEqualTo(4.2);
        assertThat(response.images()).isEqualTo(imageUrls);
        assertThat(response.addressMain()).isEqualTo(CourseFixture.DEFAULT_ADDRESS_MAIN);
        assertThat(response.addressDetail()).isEqualTo(CourseFixture.DEFAULT_ADDRESS_DETAIL);
        assertThat(response.price()).isEqualTo(CourseFixture.DEFAULT_PRICE);
        assertThat(response.capacity()).isEqualTo(CourseFixture.DEFAULT_CAPACITY);
        assertThat(response.confirmCount()).isEqualTo(5);
        assertThat(response.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(response.level()).isEqualTo(CourseFixture.DEFAULT_LEVEL);
        assertThat(response.orderOpenAt()).isEqualTo(CourseFixture.DEFAULT_ORDER_OPEN_AT);
        assertThat(response.orderCloseAt()).isEqualTo(CourseFixture.DEFAULT_ORDER_CLOSE_AT);
        assertThat(response.startAt()).isEqualTo(CourseFixture.DEFAULT_START_AT);
        assertThat(response.endAt()).isEqualTo(CourseFixture.DEFAULT_END_AT);
        assertThat(response.averageRating()).isEqualTo(4.5);
        assertThat(response.reviewCount()).isEqualTo(15L);
        assertThat(response.isWishlisted()).isFalse();
    }

    @Test
    @DisplayName("코스 상세 캐시 조회 성공 - 이미지가 없으면 빈 리스트 반환")
    void getCourseDetailCache_noImages_returnsEmptyList() {
        UUID courseId = UUID.randomUUID();
        CourseDetailQueryResult detail = sampleDetail(courseId, UUID.randomUUID());

        given(courseRepository.findCourseDetail(courseId)).willReturn(Optional.of(detail));
        given(courseImageRepository.findImageUrlsByCourseId(courseId)).willReturn(List.of());

        CourseDetailResponse response = courseCacheQueryService.getCourseDetailCache(courseId);

        assertThat(response.images()).isEmpty();
    }

    @Test
    @DisplayName("코스 상세 캐시 조회 성공 - isWishlisted는 항상 false")
    void getCourseDetailCache_isWishlistedAlwaysFalse() {
        UUID courseId = UUID.randomUUID();
        CourseDetailQueryResult detail = sampleDetail(courseId, UUID.randomUUID());

        given(courseRepository.findCourseDetail(courseId)).willReturn(Optional.of(detail));
        given(courseImageRepository.findImageUrlsByCourseId(courseId)).willReturn(List.of());

        CourseDetailResponse response = courseCacheQueryService.getCourseDetailCache(courseId);

        assertThat(response.isWishlisted()).isFalse();
    }

    @Test
    @DisplayName("코스 상세 캐시 조회 실패 - 존재하지 않는 코스이면 ERR_NOT_FOUND_COURSE")
    void getCourseDetailCache_courseNotFound_throwsException() {
        UUID courseId = UUID.randomUUID();

        given(courseRepository.findCourseDetail(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseCacheQueryService.getCourseDetailCache(courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseImageRepository, never()).findImageUrlsByCourseId(courseId);
    }

    private CourseDetailQueryResult sampleDetail(UUID courseId, UUID instructorMemberId) {
        return new CourseDetailQueryResult(
                courseId, CourseFixture.DEFAULT_TITLE, CourseFixture.DEFAULT_DESCRIPTION,
                CourseCategoryFixture.DEFAULT_CODE, CourseCategoryFixture.DEFAULT_NAME,
                instructorMemberId, "강사이름", "https://cdn.example.com/profile.jpg",
                CourseFixture.DEFAULT_ADDRESS_MAIN, CourseFixture.DEFAULT_ADDRESS_DETAIL,
                CourseFixture.DEFAULT_PRICE, CourseFixture.DEFAULT_CAPACITY, 5,
                CourseStatus.OPEN, CourseFixture.DEFAULT_LEVEL,
                CourseFixture.DEFAULT_ORDER_OPEN_AT, CourseFixture.DEFAULT_ORDER_CLOSE_AT,
                CourseFixture.DEFAULT_START_AT, CourseFixture.DEFAULT_END_AT,
                4.2, 4.5, 15L
        );
    }
}
