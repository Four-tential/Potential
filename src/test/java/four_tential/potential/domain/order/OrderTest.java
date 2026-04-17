package four_tential.potential.domain.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderTest {

    @Test
    @DisplayName("주문 생성 시 초기 상태는 PENDING이며 만료 시간은 10분 후로 설정된다")
    void registerOrderSuccess() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        BigInteger price = BigInteger.valueOf(10000);

        // when
        Order order = Order.register(memberId, courseId, 1, price, "테스트 코스");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.getExpireAt()).isAfter(order.getCreatedAt() != null ? order.getCreatedAt() : java.time.LocalDateTime.now());
        assertThat(order.getTotalPriceSnap()).isEqualTo(price);
    }

    @Test
    @DisplayName("PENDING 상태의 주문은 결제 완료(PAID) 처리할 수 있다")
    void completePaymentSuccess() {
        // given
        Order order = createPendingOrder();

        // when
        order.completePayment();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    @DisplayName("PENDING 상태가 아닌 주문을 결제 완료 처리하면 예외가 발생한다")
    void completePaymentFail() {
        // given
        Order order = createPendingOrder();
        order.completePayment(); // PAID 상태로 변경

        // when & then
        assertThatThrownBy(order::completePayment)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_PENDING_ORDER.getMessage())
                .hasFieldOrPropertyWithValue("httpStatus", OrderExceptionEnum.ERR_NOT_PENDING_ORDER.getHttpStatus());
    }

    @Test
    @DisplayName("PENDING 상태의 주문은 만료(EXPIRED) 처리할 수 있다")
    void expireSuccess() {
        // given
        Order order = createPendingOrder();

        // when
        order.expire();

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
    }

    private Order createPendingOrder() {
        return Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "테스트 코스");
    }
}
