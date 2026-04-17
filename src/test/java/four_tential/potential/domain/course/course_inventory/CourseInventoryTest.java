package four_tential.potential.domain.course.course_inventory;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.domain.course.fixture.CourseFixture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CourseInventoryTest {

    private static final UUID COURSE_ID = UUID.fromString("00000000-0000-0000-0000-000000000099");
    private static final int DEFAULT_MAX_CAPACITY = CourseFixture.DEFAULT_CAPACITY; // 20

    @Test
    @DisplayName("register() 성공 시 courseId, maxCapacity가 설정되고 confirmCount는 0")
    void register_success() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);

        assertThat(inventory.getCourseId()).isEqualTo(COURSE_ID);
        assertThat(inventory.getMaxCapacity()).isEqualTo(DEFAULT_MAX_CAPACITY);
        assertThat(inventory.getConfirmCount()).isZero();
    }

    @Test
    @DisplayName("register() - maxCapacity가 0이면 ERR_INVALID_CAPACITY 예외 발생")
    void register_zeroCapacity_throwsException() {
        assertThatThrownBy(() -> CourseInventory.register(COURSE_ID, 0))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 정원은 최소 1명 이상이어야 합니다");
    }

    @Test
    @DisplayName("register() - maxCapacity가 음수이면 ERR_INVALID_CAPACITY 예외 발생")
    void register_negativeCapacity_throwsException() {
        assertThatThrownBy(() -> CourseInventory.register(COURSE_ID, -1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 정원은 최소 1명 이상이어야 합니다");
    }

    @Test
    @DisplayName("hasAvailableSeats() - 잔여석이 충분하면 true 반환")
    void hasAvailableSeats_true() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, 10);

        assertThat(inventory.hasAvailableSeats(5)).isTrue();
    }

    @Test
    @DisplayName("hasAvailableSeats() - confirmCount + count == maxCapacity면 true (경계값)")
    void hasAvailableSeats_exactlyFull_true() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, 10);
        inventory.increase(5);

        assertThat(inventory.hasAvailableSeats(5)).isTrue();
    }

    @Test
    @DisplayName("hasAvailableSeats() - confirmCount + count > maxCapacity면 false")
    void hasAvailableSeats_false() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, 10);
        inventory.increase(8);

        assertThat(inventory.hasAvailableSeats(3)).isFalse();
    }

    @Test
    @DisplayName("hasAvailableSeats() - 정원이 가득 찬 상태에서 1 추가 시 false")
    void hasAvailableSeats_full_false() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, 5);
        inventory.increase(5);

        assertThat(inventory.hasAvailableSeats(1)).isFalse();
    }

    @Test
    @DisplayName("increase() - 잔여석이 있으면 confirmCount가 count만큼 증가")
    void increase_success() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);

        inventory.increase(3);

        assertThat(inventory.getConfirmCount()).isEqualTo(3);
    }

    @Test
    @DisplayName("increase() - 정원까지 꽉 채우는 경우 성공")
    void increase_toFullCapacity_success() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, 5);

        inventory.increase(5);

        assertThat(inventory.getConfirmCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("increase() - 정원 초과 시 ERR_IS_FULL_CAPACITY 예외 발생")
    void increase_overCapacity_throwsException() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, 5);
        inventory.increase(5);

        assertThatThrownBy(() -> inventory.increase(1))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 정원이 가득차 추가할 수 없습니다");
    }

    @Test
    @DisplayName("increase() - 여러 번 호출해도 누적 증가됨")
    void increase_multiple_times() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);

        inventory.increase(3);
        inventory.increase(2);

        assertThat(inventory.getConfirmCount()).isEqualTo(5);
    }

    @Test
    @DisplayName("decrease() - confirmCount가 양수일 때 count만큼 감소")
    void decrease_success() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);
        inventory.increase(5);

        inventory.decrease(3);

        assertThat(inventory.getConfirmCount()).isEqualTo(2);
    }

    @Test
    @DisplayName("decrease() - count가 confirmCount보다 크면 0으로 보정됨")
    void decrease_belowZero_clampedToZero() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);
        inventory.increase(3);

        inventory.decrease(10);

        assertThat(inventory.getConfirmCount()).isZero();
    }

    @Test
    @DisplayName("decrease() - confirmCount가 0일 때 호출해도 0 유지")
    void decrease_whenZero_remainsZero() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);

        inventory.decrease(1);

        assertThat(inventory.getConfirmCount()).isZero();
    }

    @Test
    @DisplayName("decrease() 후 increase() 호출 시 정원 내에서 다시 증가 가능")
    void decrease_thenIncrease_success() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, 5);
        inventory.increase(5);

        inventory.decrease(2);
        inventory.increase(1);

        assertThat(inventory.getConfirmCount()).isEqualTo(4);
    }

    @Test
    @DisplayName("updateMaxCapacity() - 정상 값이면 maxCapacity가 변경됨")
    void updateMaxCapacity_success() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);

        inventory.updateMaxCapacity(30);

        assertThat(inventory.getMaxCapacity()).isEqualTo(30);
    }

    @Test
    @DisplayName("updateMaxCapacity() - 0이면 ERR_INVALID_CAPACITY 예외 발생")
    void updateMaxCapacity_zeroCapacity_throwsException() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);

        assertThatThrownBy(() -> inventory.updateMaxCapacity(0))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 정원은 최소 1명 이상이어야 합니다");
    }

    @Test
    @DisplayName("updateMaxCapacity() - 음수이면 ERR_INVALID_CAPACITY 예외 발생")
    void updateMaxCapacity_negativeCapacity_throwsException() {
        CourseInventory inventory = CourseInventory.register(COURSE_ID, DEFAULT_MAX_CAPACITY);

        assertThatThrownBy(() -> inventory.updateMaxCapacity(-5))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage("코스의 정원은 최소 1명 이상이어야 합니다");
    }
}
