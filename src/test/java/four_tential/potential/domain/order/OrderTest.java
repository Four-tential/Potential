package four_tential.potential.domain.order;

import four_tential.potential.common.exception.ServiceErrorException;
import four_tential.potential.common.exception.domain.OrderExceptionEnum;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigInteger;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;

class OrderTest {

    @Test
    @DisplayName("주문 생성 시 초기 상태는 PENDING이며 만료 시간은 10분 후로 설정된다")
    void registerOrderSuccess() {
        // given
        UUID memberId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        BigInteger price = BigInteger.valueOf(10000);
        LocalDateTime now = LocalDateTime.now();

        // when
        Order order = Order.register(memberId, courseId, 1, price, "테스트 코스");

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.PENDING);
        // 실행 시간을 고려하여 1초 이내의 오차만 허용하고, 정확히 10분 후인지 검증
        assertThat(order.getExpireAt()).isCloseTo(now.plusMinutes(10), within(1, ChronoUnit.SECONDS));
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
    void completePaymentFail_InvalidStatus() {
        // given
        Order order = createPendingOrder();
        order.completePayment(); // PAID 상태로 변경

        // when & then
        assertThatThrownBy(order::completePayment)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_PENDING_ORDER.getMessage());
    }

    @Test
    @DisplayName("만료 시간이 지난 주문을 결제 완료 처리하면 EXPIRED 상태로 변경되고 예외가 발생한다")
    void completePaymentFail_Expired() {
        // given
        Order order = createPendingOrder();
        // 리플렉션을 사용하여 만료 시간을 과거로 설정
        ReflectionTestUtils.setField(order, "expireAt", LocalDateTime.now().minusMinutes(1));

        // when & then
        assertThatThrownBy(order::completePayment)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_ORDER_EXPIRED.getMessage());
        
        assertThat(order.getStatus()).isEqualTo(OrderStatus.EXPIRED);
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

    @Test
    @DisplayName("PENDING 상태가 아닌 주문을 만료 처리하면 예외가 발생한다")
    void expireFail() {
        // given
        Order order = createPendingOrder();
        order.completePayment(); // PAID 상태

        // when & then
        assertThatThrownBy(order::expire)
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_NOT_PENDING_ORDER.getMessage())
                .hasFieldOrPropertyWithValue("httpStatus", OrderExceptionEnum.ERR_NOT_PENDING_ORDER.getHttpStatus());
    }

    @Test
    @DisplayName("PENDING 상태의 주문은 언제든지 취소할 수 있다")
    void cancelPendingOrderSuccess() {
        // given
        Order order = createPendingOrder();
        LocalDateTime courseStartAt = LocalDateTime.now().plusDays(1); // PENDING은 날짜 제약 없음

        // when
        order.cancel(courseStartAt);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getCancelledAt()).isNotNull();
    }

    @Test
    @DisplayName("PAID 상태의 주문은 코스 시작 7일 전까지만 취소할 수 있다")
    void cancelPaidOrderSuccess() {
        // given
        Order order = createPendingOrder();
        order.completePayment(); // PAID 상태
        LocalDateTime courseStartAt = LocalDateTime.now().plusDays(8);

        // when
        order.cancel(courseStartAt);

        // then
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    @DisplayName("PAID 상태의 주문이 코스 시작 7일 이내라면 취소 시 예외가 발생한다")
    void cancelPaidOrderFail_TooLate() {
        // given
        Order order = createPendingOrder();
        order.completePayment();
        LocalDateTime courseStartAt = LocalDateTime.now().plusDays(6);

        // when & then
        assertThatThrownBy(() -> order.cancel(courseStartAt))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_DATETIME.getMessage());
    }

    @Test
    @DisplayName("이미 취소되었거나 만료된 주문은 다시 취소할 수 없다")
    void cancelOrderFail_AlreadyCancelledOrExpired() {
        // given
        Order order = createPendingOrder();
        order.expire();

        // when & then
        assertThatThrownBy(() -> order.cancel(LocalDateTime.now().plusDays(10)))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_ORDER.getMessage());
    }

    @Test
    @DisplayName("확정(CONFIRMED)된 주문은 취소할 수 없다")
    void cancelOrderFail_Confirmed() {
        // given
        Order order = createPendingOrder();
        ReflectionTestUtils.setField(order, "status", OrderStatus.CONFIRMED);

        // when & then
        assertThatThrownBy(() -> order.cancel(LocalDateTime.now().plusDays(10)))
                .isInstanceOf(ServiceErrorException.class)
                .hasMessage(OrderExceptionEnum.ERR_CANNOT_CANCEL_CONFIRMED_ORDER.getMessage());
    }

    private Order createPendingOrder() {
        return Order.register(UUID.randomUUID(), UUID.randomUUID(), 1, BigInteger.valueOf(10000), "테스트 코스");
    }
}
