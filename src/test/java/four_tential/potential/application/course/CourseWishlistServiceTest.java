package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseRepository;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.domain.course.course_wishlist.WishlistCourseQueryResult;
import four_tential.potential.domain.course.fixture.CourseFixture;
import four_tential.potential.presentation.course.model.response.CourseWishlistResponse;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CourseWishlistServiceTest {

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private CourseWishlistRepository courseWishlistRepository;

    @InjectMocks
    private CourseWishlistService courseWishlistService;

    @Test
    @DisplayName("찜 목록 조회 성공 - 1건 반환")
    void getMyWishlistCourses_success() {
        UUID memberId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        WishlistCourseQueryResult item = new WishlistCourseQueryResult(
                UUID.randomUUID(), "소도구 필라테스 입문반", "소강사",
                "https://example.com/thumb.jpg", "PILATES", "필라테스",
                BigInteger.valueOf(70000), CourseStatus.OPEN,
                LocalDateTime.now().plusDays(10), LocalDateTime.now()
        );
        given(courseWishlistRepository.findWishlistCourses(memberId, pageRequest))
                .willReturn(new PageImpl<>(List.of(item), pageRequest, 1));

        PageResponse<WishlistCourseItem> response =
                courseWishlistService.getMyWishlistCourses(memberId, 0, 10);

        assertThat(response.content()).hasSize(1);
        assertThat(response.content().getFirst().title()).isEqualTo("소도구 필라테스 입문반");
        assertThat(response.content().getFirst().memberInstructorName()).isEqualTo("소강사");
        assertThat(response.content().getFirst().status()).isEqualTo(CourseStatus.OPEN);
        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.currentPage()).isZero();
    }

    @Test
    @DisplayName("찜 목록 조회 성공 - 찜한 코스 없으면 빈 페이지 반환")
    void getMyWishlistCourses_empty() {
        UUID memberId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        given(courseWishlistRepository.findWishlistCourses(memberId, pageRequest))
                .willReturn(new PageImpl<>(List.of(), pageRequest, 0));

        PageResponse<WishlistCourseItem> response =
                courseWishlistService.getMyWishlistCourses(memberId, 0, 10);

        assertThat(response.content()).isEmpty();
        assertThat(response.totalElements()).isZero();
        assertThat(response.isLast()).isTrue();
    }

    @Test
    @DisplayName("찜 목록 조회 성공 - page=1, size=5 파라미터가 PageRequest로 올바르게 변환됨")
    void getMyWishlistCourses_customPageParams() {
        UUID memberId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(1, 5);
        given(courseWishlistRepository.findWishlistCourses(memberId, pageRequest))
                .willReturn(new PageImpl<>(List.of(), pageRequest, 7));

        PageResponse<WishlistCourseItem> response =
                courseWishlistService.getMyWishlistCourses(memberId, 1, 5);

        assertThat(response.currentPage()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(5);
        assertThat(response.totalElements()).isEqualTo(7);
        assertThat(response.totalPages()).isEqualTo(2);
    }

    @Test
    @DisplayName("찜 등록 성공 - OPEN 코스이면 isWishlisted=true 반환")
    void addWishlist_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = CourseFixture.defaultCourse();
        course.open();
        ReflectionTestUtils.setField(course, "id", courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId)).willReturn(false);

        CourseWishlistResponse response = courseWishlistService.addWishlist(memberId, courseId);

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.isWishlisted()).isTrue();
        verify(courseWishlistRepository).save(any());
    }

    @Test
    @DisplayName("찜 등록 실패 - 이미 찜한 코스이면 ERR_ALREADY_WISHLISTED")
    void addWishlist_alreadyWishlisted() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = CourseFixture.defaultCourse();
        course.open();
        ReflectionTestUtils.setField(course, "id", courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));
        given(courseWishlistRepository.existsByMemberIdAndCourseId(memberId, courseId)).willReturn(true);

        assertThatThrownBy(() -> courseWishlistService.addWishlist(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("이미 찜한 코스입니다");

        verify(courseWishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("찜 등록 실패 - 코스가 존재하지 않으면 ERR_NOT_FOUND_COURSE")
    void addWishlist_courseNotFound() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        given(courseRepository.findById(courseId)).willReturn(Optional.empty());

        assertThatThrownBy(() -> courseWishlistService.addWishlist(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseWishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("찜 등록 실패 - OPEN이 아닌 코스이면 ERR_NOT_FOUND_COURSE")
    void addWishlist_courseNotOpen() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        Course course = CourseFixture.defaultCourse();
        ReflectionTestUtils.setField(course, "id", courseId);

        given(courseRepository.findById(courseId)).willReturn(Optional.of(course));

        assertThatThrownBy(() -> courseWishlistService.addWishlist(memberId, courseId))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("존재하지 않는 코스입니다");

        verify(courseWishlistRepository, never()).save(any());
    }

    @Test
    @DisplayName("찜 해제 성공 - isWishlisted=false 반환")
    void removeWishlist_success() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        given(courseWishlistRepository.deleteByMemberIdAndCourseIdQuery(memberId, courseId)).willReturn(1);

        CourseWishlistResponse response = courseWishlistService.removeWishlist(memberId, courseId);

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.isWishlisted()).isFalse();
    }

    @Test
    @DisplayName("찜 해제 성공 - 찜 목록에 없어도 정상 응답 (멱등성)")
    void removeWishlist_notFound_stillSuccess() {
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        given(courseWishlistRepository.deleteByMemberIdAndCourseIdQuery(memberId, courseId)).willReturn(0);

        CourseWishlistResponse response = courseWishlistService.removeWishlist(memberId, courseId);

        assertThat(response.courseId()).isEqualTo(courseId);
        assertThat(response.isWishlisted()).isFalse();
    }
}
