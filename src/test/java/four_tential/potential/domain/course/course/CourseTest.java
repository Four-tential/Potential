package four_tential.potential.domain.course.course;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.fixture.CourseFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseTest {

    @Test
    @DisplayName("register() 성공 시 필수 필드가 설정되고 status는 PREPARATION, confirmCount는 0")
    void register() {
        Course course = CourseFixture.defaultCourse();

        assertThat(course.getTitle()).isEqualTo(CourseFixture.DEFAULT_TITLE);
        assertThat(course.getDescription()).isEqualTo(CourseFixture.DEFAULT_DESCRIPTION);
        assertThat(course.getCapacity()).isEqualTo(CourseFixture.DEFAULT_CAPACITY);
        assertThat(course.getPrice()).isEqualTo(CourseFixture.DEFAULT_PRICE);
        assertThat(course.getLevel()).isEqualTo(CourseFixture.DEFAULT_LEVEL);
        assertThat(course.getStatus()).isEqualTo(CourseStatus.PREPARATION);
        assertThat(course.getConfirmCount()).isZero();
        assertThat(course.getConfirmedAt()).isNull();
    }

    @Test
    @DisplayName("생성된 코스는 id가 null")
    void registerInitialState() {
        Course course = CourseFixture.defaultCourse();

        assertThat(course.getId()).isNull();
    }

    @Test
    @DisplayName("정원이 0 이하이면 INVALID_CAPACITY 예외 발생")
    void register_invalidCapacity() {
        assertThatThrownBy(() -> Course.register(
                CourseFixture.DEFAULT_COURSE_CATEGORY_ID,
                CourseFixture.DEFAULT_MEMBER_INSTRUCTOR_ID,
                CourseFixture.DEFAULT_TITLE,
                CourseFixture.DEFAULT_DESCRIPTION,
                CourseFixture.DEFAULT_ADDRESS_MAIN,
                CourseFixture.DEFAULT_ADDRESS_DETAIL,
                0,
                CourseFixture.DEFAULT_PRICE,
                CourseFixture.DEFAULT_LEVEL,
                CourseFixture.DEFAULT_ORDER_OPEN_AT,
                CourseFixture.DEFAULT_ORDER_CLOSE_AT,
                CourseFixture.DEFAULT_START_AT,
                CourseFixture.DEFAULT_END_AT
        ))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 정원은 최소 1명 이상이어야 합니다");
    }

    @Test
    @DisplayName("주문 마감 시간이 주문 오픈 시간 이전이면 INVALID_ORDER_CLOSE_TIME 예외 발생")
    void register_invalidOrderCloseTime_notAfterOpen() {
        LocalDateTime orderCloseAtBeforeOpen = CourseFixture.DEFAULT_ORDER_OPEN_AT.minusDays(1);

        assertThatThrownBy(() -> Course.register(
                CourseFixture.DEFAULT_COURSE_CATEGORY_ID,
                CourseFixture.DEFAULT_MEMBER_INSTRUCTOR_ID,
                CourseFixture.DEFAULT_TITLE,
                CourseFixture.DEFAULT_DESCRIPTION,
                CourseFixture.DEFAULT_ADDRESS_MAIN,
                CourseFixture.DEFAULT_ADDRESS_DETAIL,
                CourseFixture.DEFAULT_CAPACITY,
                CourseFixture.DEFAULT_PRICE,
                CourseFixture.DEFAULT_LEVEL,
                CourseFixture.DEFAULT_ORDER_OPEN_AT,
                orderCloseAtBeforeOpen,
                CourseFixture.DEFAULT_START_AT,
                CourseFixture.DEFAULT_END_AT
        ))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 주문 마감 시간은 코스의 주문가능 시작 시각부터 코스의 시작일시 2시간 전 까지 가능합니다");
    }

    @Test
    @DisplayName("주문 마감 시간이 코스 시작 2시간 전 이후이면 INVALID_ORDER_CLOSE_TIME 예외 발생")
    void register_invalidOrderCloseTime_notBeforeStartMinus2h() {
        LocalDateTime orderCloseAtTooLate = CourseFixture.DEFAULT_START_AT.minusHours(1);

        assertThatThrownBy(() -> Course.register(
                CourseFixture.DEFAULT_COURSE_CATEGORY_ID,
                CourseFixture.DEFAULT_MEMBER_INSTRUCTOR_ID,
                CourseFixture.DEFAULT_TITLE,
                CourseFixture.DEFAULT_DESCRIPTION,
                CourseFixture.DEFAULT_ADDRESS_MAIN,
                CourseFixture.DEFAULT_ADDRESS_DETAIL,
                CourseFixture.DEFAULT_CAPACITY,
                CourseFixture.DEFAULT_PRICE,
                CourseFixture.DEFAULT_LEVEL,
                CourseFixture.DEFAULT_ORDER_OPEN_AT,
                orderCloseAtTooLate,
                CourseFixture.DEFAULT_START_AT,
                CourseFixture.DEFAULT_END_AT
        ))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 주문 마감 시간은 코스의 주문가능 시작 시각부터 코스의 시작일시 2시간 전 까지 가능합니다");
    }

    @Test
    @DisplayName("코스 종료 일시가 시작 일시 이전이면 INVALID_SCHEDULE 예외 발생")
    void register_invalidSchedule() {
        LocalDateTime endAtBeforeStart = CourseFixture.DEFAULT_START_AT.minusHours(1);

        assertThatThrownBy(() -> Course.register(
                CourseFixture.DEFAULT_COURSE_CATEGORY_ID,
                CourseFixture.DEFAULT_MEMBER_INSTRUCTOR_ID,
                CourseFixture.DEFAULT_TITLE,
                CourseFixture.DEFAULT_DESCRIPTION,
                CourseFixture.DEFAULT_ADDRESS_MAIN,
                CourseFixture.DEFAULT_ADDRESS_DETAIL,
                CourseFixture.DEFAULT_CAPACITY,
                CourseFixture.DEFAULT_PRICE,
                CourseFixture.DEFAULT_LEVEL,
                CourseFixture.DEFAULT_ORDER_OPEN_AT,
                CourseFixture.DEFAULT_ORDER_CLOSE_AT,
                CourseFixture.DEFAULT_START_AT,
                endAtBeforeStart
        ))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 종료 일시는 코스의 시작 일시보다 이후여야 합니다");
    }

    @Test
    @DisplayName("PREPARATION 상태에서 updateInfo() 호출 시 정보가 변경됨")
    void updateInfo_inPreparation() {
        Course course = CourseFixture.defaultCourse();
        UUID newCategoryId = UUID.randomUUID();

        course.updateInfo("새 제목", "새 설명", newCategoryId);

        assertThat(course.getTitle()).isEqualTo("새 제목");
        assertThat(course.getDescription()).isEqualTo("새 설명");
        assertThat(course.getCourseCategoryId()).isEqualTo(newCategoryId);
    }

    @Test
    @DisplayName("CLOSED 상태에서 updateInfo() 호출 시 CANNOT_MODIFY_COURSE 예외 발생")
    void updateInfo_inClosed() {
        Course course = CourseFixture.defaultCourse();
        course.close();

        assertThatThrownBy(() -> course.updateInfo("제목", "설명", UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("CLOSED 또는 CANCELLED 상태의 코스는 수정할 수 없습니다");
    }

    @Test
    @DisplayName("CANCELLED 상태에서 updateInfo() 호출 시 CANNOT_MODIFY_COURSE 예외 발생")
    void updateInfo_inCancelled() {
        Course course = CourseFixture.defaultCourse();
        course.cancel();

        assertThatThrownBy(() -> course.updateInfo("제목", "설명", UUID.randomUUID()))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("CLOSED 또는 CANCELLED 상태의 코스는 수정할 수 없습니다");
    }

    @Test
    @DisplayName("PREPARATION 상태에서 updateInfoInPreparation() 성공 시 가격과 정원이 변경됨")
    void updateInfoInPreparation_success() {
        Course course = CourseFixture.defaultCourse();

        course.updateInfoInPreparation(
                BigInteger.valueOf(100000),
                30,
                "새 주소",
                "새 상세주소",
                CourseFixture.DEFAULT_ORDER_OPEN_AT,
                CourseFixture.DEFAULT_ORDER_CLOSE_AT,
                CourseFixture.DEFAULT_START_AT,
                CourseFixture.DEFAULT_END_AT
        );

        assertThat(course.getPrice()).isEqualTo(BigInteger.valueOf(100000));
        assertThat(course.getCapacity()).isEqualTo(30);
        assertThat(course.getAddressMain()).isEqualTo("새 주소");
        assertThat(course.getAddressDetail()).isEqualTo("새 상세주소");
    }

    @Test
    @DisplayName("OPEN 상태에서 updateInfoInPreparation() 호출 시 IMMUTABLE_FIELD_IN_OPEN 예외 발생")
    void updateInfoInPreparation_inOpen() {
        Course course = CourseFixture.defaultCourse();
        course.confirm();

        assertThatThrownBy(() -> course.updateInfoInPreparation(
                CourseFixture.DEFAULT_PRICE,
                CourseFixture.DEFAULT_CAPACITY,
                CourseFixture.DEFAULT_ADDRESS_MAIN,
                CourseFixture.DEFAULT_ADDRESS_DETAIL,
                CourseFixture.DEFAULT_ORDER_OPEN_AT,
                CourseFixture.DEFAULT_ORDER_CLOSE_AT,
                CourseFixture.DEFAULT_START_AT,
                CourseFixture.DEFAULT_END_AT
        ))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("OPEN 상태에서는 가격, 일정, 장소, 정원을 수정할 수 없습니다");
    }

    @Test
    @DisplayName("confirm() 호출 시 status가 OPEN이 되고 confirmedAt이 설정됨")
    void confirm() {
        Course course = CourseFixture.defaultCourse();
        LocalDateTime before = LocalDateTime.now();

        course.confirm();

        LocalDateTime after = LocalDateTime.now();
        assertThat(course.getStatus()).isEqualTo(CourseStatus.OPEN);
        assertThat(course.getConfirmedAt()).isAfterOrEqualTo(before).isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("PREPARATION 이 아닌 상태에서 confirm() 호출 시 INVALID_STATUS_TRANSITION_TO_CONFIRM 예외 발생")
    void confirm_notInPreparation() {
        Course course = CourseFixture.defaultCourse();
        course.confirm();

        assertThatThrownBy(course::confirm)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("준비 상태의 코스만 개설 확정할 수 있습니다");
    }

    @Test
    @DisplayName("increaseConfirmCount() 호출 시 confirmCount가 1 증가")
    void increaseConfirmCount() {
        Course course = CourseFixture.defaultCourse();

        course.increaseConfirmCount();

        assertThat(course.getConfirmCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("confirmCount가 0일 때 decreaseConfirmCount() 호출 시 0 유지")
    void decreaseConfirmCount_whenZero() {
        Course course = CourseFixture.defaultCourse();

        course.decreaseConfirmCount();

        assertThat(course.getConfirmCount()).isZero();
    }

    @Test
    @DisplayName("confirmCount가 capacity 이상이면 isFull()은 true")
    void isFull_true() {
        Course course = CourseFixture.defaultCourse();
        for (int i = 0; i < CourseFixture.DEFAULT_CAPACITY; i++) {
            course.increaseConfirmCount();
        }

        assertThat(course.isFull()).isTrue();
    }

    @Test
    @DisplayName("confirmCount가 capacity 미만이면 isFull()은 false")
    void isFull_false() {
        Course course = CourseFixture.defaultCourse();

        assertThat(course.isFull()).isFalse();
    }
}
