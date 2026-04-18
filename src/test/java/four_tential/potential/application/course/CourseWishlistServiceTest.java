package four_tential.potential.application.course;

import four_tential.potential.common.dto.PageResponse;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course_wishlist.CourseWishlistRepository;
import four_tential.potential.presentation.member.model.response.WishlistCourseItem;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

@ExtendWith(MockitoExtension.class)
class CourseWishlistServiceTest {

    @Mock
    private CourseWishlistRepository courseWishlistRepository;

    @InjectMocks
    private CourseWishlistService courseWishlistService;

    @Test
    @DisplayName("찜 목록 조회 성공 - 1건 반환")
    void getMyWishlistCourses_success() {
        UUID memberId = UUID.randomUUID();
        PageRequest pageRequest = PageRequest.of(0, 10);
        WishlistCourseItem item = new WishlistCourseItem(
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
}
