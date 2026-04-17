package four_tential.potential.application.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PaymentDistributedLockExecutorTest {

    private final PaymentDistributedLockExecutor executor = new PaymentDistributedLockExecutor();

    @Test
    @DisplayName("orderId가 null이면 주문 기준 락 실행을 거부한다")
    void executeWithOrderLock_nullOrderId_throwsException() {
        assertThatThrownBy(() -> executor.executeWithOrderLock(null, () -> "ignored"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("orderId must not be null");
    }

    @Test
    @DisplayName("courseId가 null이면 코스 기준 락 실행을 거부한다")
    void executeWithCourseLock_nullCourseId_throwsException() {
        assertThatThrownBy(() -> executor.executeWithCourseLock(null, () -> "ignored"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("courseId must not be null");
    }

    @Test
    @DisplayName("pgKey가 null이면 결제 식별자 기준 락 실행을 거부한다")
    void executeWithPgKeyLock_nullPgKey_throwsException() {
        assertThatThrownBy(() -> executor.executeWithPgKeyLock(null, () -> "ignored"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pgKey must not be blank");
    }

    @Test
    @DisplayName("pgKey가 빈 값이면 결제 식별자 기준 락 실행을 거부한다")
    void executeWithPgKeyLock_blankPgKey_throwsException() {
        assertThatThrownBy(() -> executor.executeWithPgKeyLock(" ", () -> "ignored"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("pgKey must not be blank");
    }

    @Test
    @DisplayName("정상 입력이면 action 결과를 그대로 반환한다")
    void executeWithLock_validInput_returnsActionResult() {
        UUID orderId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();

        String orderResult = executor.executeWithOrderLock(orderId, () -> "order");
        String courseResult = executor.executeWithCourseLock(courseId, () -> "course");
        String pgKeyResult = executor.executeWithPgKeyLock("pg-key-1", () -> "pg");

        assertThat(orderResult).isEqualTo("order");
        assertThat(courseResult).isEqualTo("course");
        assertThat(pgKeyResult).isEqualTo("pg");
    }
}
