package four_tential.potential.presentation.course.model.response;

import four_tential.potential.domain.attendance.AttendanceStatus;
import four_tential.potential.domain.course.course.CourseLevel;
import four_tential.potential.domain.course.course.CourseListQueryResult;
import four_tential.potential.domain.course.course.CourseStatus;
import four_tential.potential.domain.course.course.InstructorCourseQueryResult;
import four_tential.potential.domain.order.CourseStudentQueryResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class CourseResponseDtoTest {

    @Test
    @DisplayName("CourseListItem.register는 쿼리 결과와 위시리스트 상태를 올바르게 매핑한다")
    void courseListItem_register() {
        UUID courseId = UUID.randomUUID();
        UUID instructorId = UUID.randomUUID();
        LocalDateTime orderOpenAt = LocalDateTime.of(2025, 5, 1, 10, 0);
        LocalDateTime startAt = LocalDateTime.of(2025, 6, 1, 10, 0);

        CourseListQueryResult result = new CourseListQueryResult(
                courseId, "초급 하타 요가", "YOGA", "요가",
                instructorId, "김강사", "https://cdn.example.com/profile.jpg",
                "https://cdn.example.com/thumb.jpg",
                BigInteger.valueOf(120000), 20, 15,
                CourseStatus.OPEN, CourseLevel.BEGINNER,
                orderOpenAt, startAt
        );

        CourseListItem item = CourseListItem.register(result, true);

        assertThat(item.courseId()).isEqualTo(courseId);
        assertThat(item.title()).isEqualTo("초급 하타 요가");
        assertThat(item.categoryCode()).isEqualTo("YOGA");
        assertThat(item.instructor().memberId()).isEqualTo(instructorId);
        assertThat(item.instructor().name()).isEqualTo("김강사");
        assertThat(item.price()).isEqualTo(BigInteger.valueOf(120000));
        assertThat(item.capacity()).isEqualTo(20);
        assertThat(item.confirmCount()).isEqualTo(15);
        assertThat(item.status()).isEqualTo(CourseStatus.OPEN);
        assertThat(item.level()).isEqualTo(CourseLevel.BEGINNER);
        assertThat(item.isWishlisted()).isTrue();
    }

    @Test
    @DisplayName("CourseListItem.register - 위시리스트 미등록")
    void courseListItem_register_notWishlisted() {
        CourseListQueryResult result = new CourseListQueryResult(
                UUID.randomUUID(), "필라테스 입문", "PILATES", "필라테스",
                UUID.randomUUID(), "이강사", null, null,
                BigInteger.valueOf(150000), 15, 5,
                CourseStatus.OPEN, CourseLevel.BEGINNER,
                LocalDateTime.now(), LocalDateTime.now().plusDays(30)
        );

        CourseListItem item = CourseListItem.register(result, false);

        assertThat(item.isWishlisted()).isFalse();
    }

    @Test
    @DisplayName("CourseStudentItem.register는 수강생 정보를 올바르게 매핑한다")
    void courseStudentItem_register() {
        UUID memberId = UUID.randomUUID();
        LocalDateTime attendanceAt = LocalDateTime.of(2025, 6, 1, 10, 5);
        CourseStudentQueryResult result = new CourseStudentQueryResult(
                memberId, "홍길동", AttendanceStatus.ATTEND, attendanceAt
        );

        CourseStudentItem item = CourseStudentItem.register(result);

        assertThat(item.memberId()).isEqualTo(memberId);
        assertThat(item.memberName()).isEqualTo("홍길동");
        assertThat(item.attendanceStatus()).isEqualTo(AttendanceStatus.ATTEND);
        assertThat(item.attendanceAt()).isEqualTo(attendanceAt);
    }

    @Test
    @DisplayName("InstructorCourseListItem.register는 강사 코스 정보를 올바르게 매핑한다")
    void instructorCourseListItem_register() {
        UUID courseId = UUID.randomUUID();
        InstructorCourseQueryResult result = new InstructorCourseQueryResult(
                courseId, "고급 요가", CourseLevel.ADVANCE, CourseStatus.OPEN,
                10, 8, BigInteger.valueOf(200000),
                LocalDateTime.of(2025, 5, 1, 10, 0),
                LocalDateTime.of(2025, 6, 1, 10, 0)
        );

        InstructorCourseListItem item = InstructorCourseListItem.register(result);

        assertThat(item.courseId()).isEqualTo(courseId);
        assertThat(item.title()).isEqualTo("고급 요가");
        assertThat(item.level()).isEqualTo(CourseLevel.ADVANCE);
        assertThat(item.capacity()).isEqualTo(10);
        assertThat(item.confirmCount()).isEqualTo(8);
        assertThat(item.price()).isEqualTo(BigInteger.valueOf(200000));
    }
}
