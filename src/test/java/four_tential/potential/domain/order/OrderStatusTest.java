package four_tential.potential.domain.order;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OrderStatus")
class OrderStatusTest {

    @Test
    @DisplayName("PENDING 상태의 설명은 '결제 대기'이다")
    void pendingDescriptionIsCorrect() {
        assertThat(OrderStatus.PENDING.getDescription()).isEqualTo("결제 대기");
    }

    @Test
    @DisplayName("PAID 상태의 설명은 '결제 완료'이다")
    void paidDescriptionIsCorrect() {
        assertThat(OrderStatus.PAID.getDescription()).isEqualTo("결제 완료");
    }

    @Test
    @DisplayName("CONFIRMED 상태의 설명은 '결제 확정'이다")
    void confirmedDescriptionIsCorrect() {
        assertThat(OrderStatus.CONFIRMED.getDescription()).isEqualTo("결제 확정");
    }

    @Test
    @DisplayName("CANCELLED 상태의 설명은 '주문 취소'이다")
    void cancelledDescriptionIsCorrect() {
        assertThat(OrderStatus.CANCELLED.getDescription()).isEqualTo("주문 취소");
    }

    @Test
    @DisplayName("EXPIRED 상태의 설명은 '결제 만료'이다")
    void expiredDescriptionIsCorrect() {
        assertThat(OrderStatus.EXPIRED.getDescription()).isEqualTo("결제 만료");
    }

    @Test
    @DisplayName("OrderStatus는 정확히 5개의 값을 가진다")
    void hasExactlyFiveValues() {
        assertThat(OrderStatus.values()).hasSize(5);
    }

    @Test
    @DisplayName("모든 OrderStatus 값을 이름으로 조회할 수 있다")
    void allValuesAreAccessibleByName() {
        assertThat(OrderStatus.valueOf("PENDING")).isEqualTo(OrderStatus.PENDING);
        assertThat(OrderStatus.valueOf("PAID")).isEqualTo(OrderStatus.PAID);
        assertThat(OrderStatus.valueOf("CONFIRMED")).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(OrderStatus.valueOf("CANCELLED")).isEqualTo(OrderStatus.CANCELLED);
        assertThat(OrderStatus.valueOf("EXPIRED")).isEqualTo(OrderStatus.EXPIRED);
    }

    @ParameterizedTest
    @EnumSource(OrderStatus.class)
    @DisplayName("모든 OrderStatus 값은 null이 아닌 설명을 가진다")
    void allStatusesHaveNonNullDescription(OrderStatus status) {
        assertThat(status.getDescription()).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("OrderStatus 값들의 순서가 올바르다")
    void valuesAreInCorrectOrder() {
        OrderStatus[] values = OrderStatus.values();
        assertThat(values[0]).isEqualTo(OrderStatus.PENDING);
        assertThat(values[1]).isEqualTo(OrderStatus.PAID);
        assertThat(values[2]).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(values[3]).isEqualTo(OrderStatus.CANCELLED);
        assertThat(values[4]).isEqualTo(OrderStatus.EXPIRED);
    }
}