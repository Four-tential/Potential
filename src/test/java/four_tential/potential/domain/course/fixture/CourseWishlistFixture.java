package four_tential.potential.domain.course.fixture;

import four_tential.potential.domain.course.course_wishlist.CourseWishlist;

import java.util.UUID;

public class CourseWishlistFixture {

    public static final UUID DEFAULT_MEMBER_ID = UUID.fromString("00000000-0000-0000-0000-000000000010");
    public static final UUID DEFAULT_COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000011");

    public static CourseWishlist defaultCourseWishlist() {
        return CourseWishlist.register(DEFAULT_MEMBER_ID, DEFAULT_COURSE_ID);
    }
}
