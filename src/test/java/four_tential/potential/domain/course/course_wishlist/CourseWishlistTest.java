package four_tential.potential.domain.course.course_wishlist;

import four_tential.potential.domain.course.fixture.CourseWishlistFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CourseWishlistTest {

    @Test
    @DisplayName("register() 성공 시 memberId와 courseId가 설정됨")
    void register() {
        CourseWishlist wishlist = CourseWishlistFixture.defaultCourseWishlist();

        assertThat(wishlist.getMemberId()).isEqualTo(CourseWishlistFixture.DEFAULT_MEMBER_ID);
        assertThat(wishlist.getCourseId()).isEqualTo(CourseWishlistFixture.DEFAULT_COURSE_ID);
    }

    @Test
    @DisplayName("생성된 위시리스트는 id가 null")
    void registerInitialState() {
        CourseWishlist wishlist = CourseWishlistFixture.defaultCourseWishlist();

        assertThat(wishlist.getId()).isNull();
    }
}
