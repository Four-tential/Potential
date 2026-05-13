package four_tential.potential.domain.course.fixture;

import four_tential.potential.domain.course.course.Course;
import four_tential.potential.domain.course.course.CourseLevel;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

public class CourseFixture {

    public static final UUID DEFAULT_COURSE_CATEGORY_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");
    public static final UUID DEFAULT_MEMBER_INSTRUCTOR_ID = UUID.fromString("00000000-0000-0000-0000-000000000002");
    public static final String DEFAULT_TITLE = "자바 스프링 부트 입문";
    public static final String DEFAULT_DESCRIPTION = "스프링 부트를 처음 배우는 분들을 위한 코스입니다";
    public static final String DEFAULT_ADDRESS_MAIN = "서울특별시 강남구";
    public static final String DEFAULT_ADDRESS_DETAIL = "테헤란로 123 4층";
    public static final int DEFAULT_CAPACITY = 20;
    public static final BigInteger DEFAULT_PRICE = BigInteger.valueOf(50000);
    public static final CourseLevel DEFAULT_LEVEL = CourseLevel.BEGINNER;
    public static final LocalDateTime DEFAULT_ORDER_OPEN_AT = LocalDateTime.of(2026, 1, 1, 9, 0);
    public static final LocalDateTime DEFAULT_ORDER_CLOSE_AT = LocalDateTime.of(2026, 1, 10, 9, 0);
    public static final LocalDateTime DEFAULT_START_AT = LocalDateTime.of(2026, 1, 12, 9, 0);
    public static final LocalDateTime DEFAULT_END_AT = LocalDateTime.of(2026, 1, 12, 18, 0);

    public static Course defaultCourse() {
        return Course.register(
                DEFAULT_COURSE_CATEGORY_ID,
                DEFAULT_MEMBER_INSTRUCTOR_ID,
                DEFAULT_TITLE,
                DEFAULT_DESCRIPTION,
                DEFAULT_ADDRESS_MAIN,
                DEFAULT_ADDRESS_DETAIL,
                DEFAULT_CAPACITY,
                DEFAULT_PRICE,
                DEFAULT_LEVEL,
                DEFAULT_ORDER_OPEN_AT,
                DEFAULT_ORDER_CLOSE_AT,
                DEFAULT_START_AT,
                DEFAULT_END_AT
        );
    }
}
